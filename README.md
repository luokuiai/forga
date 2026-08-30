# Forga

Forga (Fine-grained Object-Relation Graph Authorization) is an embedded, domain-neutral
authorization SDK for Java applications. It supports unified RBAC, ReBAC, and ABAC policy
evaluation over relationships and attributes supplied by the host application.

Forga is not an identity system, permission-management product, tenant framework, or relationship
database. Host applications keep their own accounts, business resources, relationship storage, and
permission-management UI. Forga provides the authorization model, bounded evaluator, resolver
contracts, query constraints, and optional framework integrations.

## Architecture

![Forga architecture](docs/architecture/forga-architecture.svg)

The host application owns identity selection, business data, relationship implementations, and
schema mappings. Forga owns the neutral policy model, bounded authorization evaluation, typed query
constraints, and optional framework integration.

## When To Use It

Use Forga when an application needs authorization such as:

- A subject can view or edit a resource because of a direct role or relationship.
- A resource inherits permissions through a group, parent object, folder, workspace, project, or
  other host-defined boundary.
- A subject can operate in multiple boundaries and must select an active boundary before acting.
- A list query must be constrained by authorization instead of loading rows and checking them one by
  one.
- Authorization data already exists in host tables or services and should not be copied into a
  Forga-owned store.

## Modules

- `forga-core`: references, policy expressions, compiled policies, bounded `check`, `bulkCheck`,
  and `listObjects` evaluation, plus host-owned relationship and attribute resolver contracts.
- `forga-query`: typed query constraints for pushing authorization into host queries.
- `forga-mybatis`: MyBatis SQL translation and statement interception helpers.
- `forga-sa-token`: optional Sa-Token authenticated-subject adapter.
- `forga-spring-security`: optional Spring Security authenticated-subject adapter.
- `forga-spring-boot-starter`: opt-in runtime assembly and MyBatis auto-configuration.
- `forga-scope`: scope switching, active-scope checks, acting context, and scope query helpers.
- `forga-spring-web`: endpoint permission resolution and Spring MVC interceptor integration.

## Design Model

Forga evaluates opaque references:

```text
subject + permission + object + attributes -> decision
```

The host decides what the names mean:

```text
SubjectRef("user", "alice")
ObjectRef("document", "doc-1")
RelationRef("viewer")
PermissionRef("view")
AttributeRef("region")
```

Forga compares these values and evaluates policy expressions. It does not assign business meaning
to object types, subject types, relations, permissions, caveats, attributes, or scopes.

The key design boundary is:

```text
Host application owns data.
Forga owns authorization evaluation.
```

## Authorization Styles

Forga supports RBAC, ReBAC, and ABAC using one policy model.

RBAC is represented as relationships from a role-like object or scope to a subject:

```text
scope:alpha#admin@subject:alice
permission manage = relation(admin)
```

ReBAC is represented as relationships between subjects, objects, and object sets:

```text
document:roadmap#viewer@subject:alice
document:roadmap#parent@folder:strategy#member
folder:strategy#member@subject:alice
```

ABAC is represented with caveats and request attributes:

```text
permission view = caveat(relation(viewer), business_hours)
attributes: business_hours=true
```

These styles can be composed in one expression with union, intersection, exclusion, traversal, and
caveat nodes.

## Basic Check

Define a policy:

```java
RelationRef viewer = new RelationRef("viewer");
PermissionRef view = new PermissionRef("view");

CompiledPolicy policy =
    PolicyCompiler.compile(
        new PolicyDefinition(Map.of(view, PermissionExpression.relation(viewer))));
```

Create an evaluator with a host resolver:

```java
AuthorizationEvaluator evaluator =
    new AuthorizationEvaluator(policy, relationshipLookup, EvaluationLimits.defaults());

CheckDecision decision =
    evaluator.check(
        new CheckRequest(
            new ObjectRef("document", "doc-1"),
            new PermissionRef("view"),
            new SubjectRef("user", "alice")));
```

`decision.allowed()` is true only when the resolver can prove the relationship required by the
policy. Unknown permissions, resolver failures, cycle detection, limit exhaustion, and consistency
conflicts fail closed.

`check()` is for one independently identified object. **Do not query a collection and call
`check()` once per row**: every call has independent evaluation state and can turn host grant or
relationship lookups into N+1 queries. For a bounded set of already identified objects, submit one
`bulkCheck()` call so Forga can batch lookups and share request-scoped memoization:

```java
List<CheckDecision> decisions =
    evaluator.bulkCheck(
        documents.stream()
            .map(document -> new CheckRequest(document.ref(), view, currentSubject))
            .toList());
```

`bulkCheck()` prevents the SDK from issuing one lookup per decision, but host implementations must
also resolve each submitted batch with set-oriented repository queries rather than looping and
querying once per request. For normal paginated business lists, use a MyBatis query constraint or
authorized rowset instead of either form of per-row authorization.

## Host Resolvers

Applications expose existing authorization data through resolver contracts. A resolver can read
from any host-owned table, cache, service, or graph, but it returns neutral Forga references.

`forga-core` provides independent resolver contracts. A host adapter implements only the operations
it owns and may implement several on one named class:

```java
ForwardRelationshipResolver relationships = ...;
ReverseRelationshipResolver reverseRelationships = ...;
AttributeResolver attributes = ...;
ResolverRegistry registry =
    new ResolverRegistry(List.of(relationships, reverseRelationships, attributes));
```

`forga-core` evaluates against lower-level lookup contracts:

```java
RelationshipLookup relationshipLookup = requests -> ...;
ObjectListingLookup objectListingLookup = requests -> ...;
```

Reusable adapters connect a resolver registry to both evaluator lookup contracts:

```java
RelationshipLookup relationshipLookup =
    new ResolverRegistryRelationshipLookup(registry);
ObjectListingLookup objectListingLookup =
    new ResolverRegistryObjectListingLookup(registry);
AttributeLookup attributeLookup =
    new ResolverRegistryAttributeLookup(registry);
```

Forward resolution powers `check` and `bulkCheck`. Reverse resolution powers `listObjects`.
Request attributes remain caller-scoped input. Caveats separately declare resolver-owned object
attributes, which the evaluator loads in bounded batches for the current object, including objects
reached through traversal and bounded `listObjects` candidates. A forward-only resolver implements
only `name()`, `forwardRelations()`, and `resolveForward()`.

Forga does not require a Forga-owned relationship table. Hosts may store relationships in their own
schema, derive them from business tables, or resolve them from external services.

### Batch Lookup Contract

`PermissionGrantLookup` and every specialized resolver operation receive batches so host adapters
can avoid N+1 queries. A correct database-backed implementation should:

1. Read one request-consistent snapshot or cache version for the batch.
2. Extract the distinct tenant, subject, object, relation, attribute, and permission keys needed by
   that batch.
3. Load matching rows with one or a fixed number of set-oriented `IN`, join, or repository batch
   queries.
4. Group the loaded rows in memory and return exactly one result for every submitted request.

Do not hide per-request database access inside the batch callback:

```java
// Wrong: one repository query for every request.
for (CheckRequest request : requests) {
  results.put(request, repository.findOne(request));
}
```

The SDK validates response completeness and limits batch sizes, but it cannot inspect how many SQL
statements a host repository executes. Adapter tests should therefore count repository calls and
assert that query count remains fixed as batch size grows.

## Dynamic Permission Grants

Subject-centric RBAC systems can keep permission expressions fixed while resolving effective role
grants from a host-owned snapshot. Use `grant()` instead of converting a permission snapshot into a
list of every authorized user:

```java
CompiledPolicy policy =
    PolicyCompiler.compile(
        new PolicyDefinition(Map.of(view, PermissionExpression.grant())));

PermissionGrantLookup grants =
    (requests, context) -> {
      HostSnapshot snapshot = hostSnapshots.resolve(context.consistency());
      return new BatchResolution<>(
          requests.stream()
              .collect(Collectors.toUnmodifiableMap(
                  request -> request,
                  request -> snapshot.permissions(request.subject())
                      .contains(request.permission()))),
          Optional.of(snapshot.consistencyToken()));
    };
```

The lookup receives complete subject, object, permission, request attributes, current consistency,
and deadline in bounded batches. It can reuse a versioned host cache and return one opaque
`ConsistencyToken` for the complete batch. Role assignments,
permission tables, snapshot invalidation, and cache storage remain host-owned. A grant leaf can be
combined with relations and caveats, for example `grant() OR relation(owner)`. Grant leaves are
check-only; object listing still requires reverse relationship resolution or a set-oriented query.

This keeps two different kinds of configuration separate:

- Permission expressions are the stable authorization model compiled into `CompiledPolicy`.
- Role assignments, role-permission grants, data-scope grants, relationships, and attributes are
  dynamic host authorization data read during checks or query-boundary resolution.

Changing host authorization data does not require rebuilding `CompiledPolicy`. For ABAC-style data
scopes, resolve the effective host grant once per query and translate it to an allowlisted,
parameterized `QueryConstraint` through `MyBatisAuthorizationBoundaryResolver`.
The runnable example uses `OWNER`, `DEPARTMENT`, and `TENANT`; even tenant-wide access becomes an
explicit `tenant_id` predicate rather than an unconstrained query.

## Object Listing

`listObjects` discovers objects from reverse relationship resolver pages:

```java
ListObjectsResponse response =
    evaluator.listObjects(
        new ListObjectsRequest(
            "document",
            new PermissionRef("view"),
            new SubjectRef("user", "alice"),
            50));
```

This is not a table scan plus per-row `check`. The host resolver must provide reverse lookup pages.
Objects that are not discoverable from reverse relationships are outside graph listing. For normal
business list pages, use query constraints instead.

`listObjects()` may materialize relation unions, intersections, and traversal results before
returning a page. `pageSize` limits the response page, not every intermediate relation set; the
evaluator bounds those sets with `maxIntermediateResults` and fails closed when the limit is
exceeded. Use graph listing only for bounded relationship discovery. Large or routinely paginated
business datasets should push authorization into SQL through a query constraint or authorized
rowset instead of increasing the intermediate-result limit.

Listing cursors are opaque and bound to the request identity, policy fingerprint, consistency
context, and resolver continuation state. Reusing a cursor with different request inputs fails
closed.

## Query Constraints

`forga-query` represents authorization filters as typed fields, parameters, predicates, joins,
correlated existence checks, and boolean composition. `forga-mybatis` translates only allowlisted
fields into parameterized SQL fragments.

This is the path for business list pages:

```text
host request -> subject + active scope -> query constraint -> parameterized SQL
```

Unknown fields, unsafe identifiers, or unsupported constraint nodes are rejected before SQL
execution.

For ReBAC list pages, prefer an authorized rowset plan instead of per-row `check` calls. The host
maps a business resource and an authorization rowset, then Forga generates one SQL statement that
joins them before filtering, ordering, and pagination:

```text
business_resource
JOIN authorized_rowset ON resource.id = authorized_rowset.object_id
WHERE authorized_rowset.subject_id = #{forga.parameters.subject}
ORDER BY authorized_rowset.rank DESC, resource.created_at DESC
LIMIT ...
```

The authorization rowset can be a host table, view, materialized view, or queryable relation
projection. It can expose fields such as relation, scope, rank, source, or assignment status.
Those fields can be selected with stable aliases and used for ordering before pagination:

```java
AuthorizedListQuery listQuery =
    QueryConstraintGenerator.authorizedRowset(
        taskMapping,
        accessMapping,
        "id",
        "object_id",
        QueryConstraint.predicate(
            accessMapping.field("subject_id"),
            PredicateOperator.EQUALS,
            new QueryParameter("subject", QueryValueType.STRING)),
        List.of(new QueryProjection(accessMapping.field("relation"), "forga_relation")),
        List.of(new QueryOrdering(accessMapping.field("rank"), QuerySortDirection.DESC)));

MyBatisAuthorizationBoundary boundary =
    MyBatisAuthorizationBoundary.list("task-list", listQuery);
```

This keeps pagination correct because the database filters and sorts the authorized rowset before
returning a page. It also keeps relationship context available for the UI without loading a page of
business rows and authorizing each row separately.

## Scope And Concurrent Roles

`forga-scope` handles applications where one subject can operate under multiple authorization
boundaries. A scope can represent a host-owned boundary such as a workspace, project, organization,
department, tenant, or another partition. Scope names are examples; hosts map their own ids to
`ScopeRef`.

The scope package provides:

- `ScopeRef`: opaque boundary reference.
- `ActiveScope`: selected boundary for the current request.
- `ScopedSubject`: subject plus optional active scope.
- `ActingScopeContext`: original subject, acting subject, and active scope.
- `ScopeSwitchRequest` / `ScopeSwitchDecision`: check whether a subject can enter a scope.
- `ScopedPermissionRequest` / `ScopedPermissionDecision`: check a permission and identify its scope
  authorization phase.
- `ObjectScopeLookup`: batch-resolve the host-owned scopes containing protected objects.
- `CrossScopeGrantLookup`: batch-prove explicit grants when active and object scopes differ.
- `ScopePolicyTemplates`: `member`, `assigned`, `denied`, and `enter` policy helpers.
- `ScopeQueryConstraints`: parameterized active-scope and active-or-granted list predicates.

Concurrent cross-boundary roles should be stored as subject-scope relationships, not account-owned
state. For example:

```text
scope:alpha#member@subject:alice
scope:beta#assigned@subject:alice
scope:beta#denied@subject:bob
```

An application may return an aggregated list for switching UI:

```json
[
  {
    "scopeType": "workspace",
    "scopeId": "alpha",
    "relation": "member",
    "role": "admin",
    "primary": true
  },
  {
    "scopeType": "workspace",
    "scopeId": "beta",
    "relation": "assigned",
    "role": "reviewer",
    "primary": false
  }
]
```

That list is display state. Authorization still evaluates the underlying relationships and the
selected `ActiveScope`.

Scope switch example:

```java
ObjectScopeLookup objectScopes = hostObjects::resolveScopes;
ScopedAuthorizationService switchService =
    new ScopedAuthorizationService(evaluator, objectScopes);

ScopeSwitchDecision decision =
    switchService.canSwitch(
        new ScopeSwitchRequest(
            new SubjectRef("user", "alice"),
            new ScopeRef("workspace", "beta")));
```

Scoped permission example:

```java
ObjectScopeLookup objectScopes = hostObjects::resolveScopes;
CrossScopeGrantLookup crossScopeGrants = hostGrants::resolveBatch;
ScopedAuthorizationService service =
    new ScopedAuthorizationService(evaluator, objectScopes, crossScopeGrants);

ScopedPermissionDecision decision =
    service.check(
        new ScopedPermissionRequest(
            new ObjectRef("task", "task-1"),
            new PermissionRef("edit"),
            ScopedSubject.of(
                new SubjectRef("user", "alice"),
                new ActiveScope(new ScopeRef("workspace", "beta")))));
```

Strict construction first verifies that the subject can enter the active scope, resolves the
object's owning scope, requires an explicit host grant when the scopes differ, and only then
evaluates the requested object permission. Missing ownership, denied grants, and resolver failures
fail closed. `bulkCheck` applies the same stages to bounded request batches without issuing one
ownership, grant, or evaluator call per object.

For list queries that include explicitly granted objects, compose a host-owned set-based grant
predicate with `ScopeQueryConstraints.activeOrGranted(...)`. This is only the scope boundary
fragment: verify scope entry first and combine it with the ordinary object-permission constraint.
Keep the grant predicate consistent with `CrossScopeGrantLookup`; do not authorize a page by
calling `check` once per row.

Object ownership, cross-scope grants, and relationship resolution participating in one decision
should observe the same host transaction or request-consistent snapshot. Otherwise concurrent
ownership or grant changes can make the separate checks disagree.

## Permission Catalog

Business modules declare stable permissions independently of where those permissions are used:

```java
PermissionDefinition view =
    new PermissionDefinition(
        new PermissionRef("meeting:view"),
        "View meeting",
        "meeting");
PermissionCatalog catalog =
    PermissionCatalog.fromContributors(List.of(() -> List.of(view)));
```

Hosts implement `PermissionCatalogSynchronizer` to upsert this catalog into their own permission
tables. When Forga is enabled, the Spring Boot starter invokes the configured synchronizer after
catalog assembly. Forga does not define tables, deletion rules, role assignments, or administration
flows. Catalog entries do not have to appear on a Web endpoint, so jobs and message consumers can
share the same assignable permissions.

## Spring Web Permission Resolution

Business systems can use the optional Forga annotation:

```java
@RequiresPermission("meeting:view")
public MeetingDetail getMeeting(String meetingId) {
  return meetingService.getMeeting(meetingId);
}
```

Public handlers use Jakarta `@PermitAll`. An unannotated handler is unresolved and fails closed by
default.

For compiled SDK controllers or other handlers that the host cannot annotate, contribute exact
controller method registrations:

```java
@Bean
EndpointPermissionContributor vendorSdkPermissions() {
  PermissionDefinition view =
      new PermissionDefinition(
          new PermissionRef("vendor:order:view"),
          "View vendor order",
          "vendor-sdk");

  return registry ->
      registry.require(
          VendorOrderController.class,
          "getOrder",
          view,
          String.class);
}
```

The controller type, method name, and parameter types identify one exact handler and disambiguate
overloads. Startup fails when the method is missing, is not a Spring MVC handler, or conflicts with
annotation metadata. Required permission definitions enter the ordinary `PermissionCatalog`
automatically; permit-all registrations add no catalog entry.

When Forga is enabled and endpoint contributors plus an `EndpointPermissionAuthorizer` are present,
the Spring Boot Starter compiles the registrations and installs the MVC interceptor automatically.
Existing annotation-only integrations may continue registering the interceptor directly. Hosts
with request-dependent metadata can still implement `EndpointPermissionResolver`; its result is
composed with annotations and registrations, and conflicting results fail closed.

The host authorizer maps the resolved permission and request context into Forga checks:

```java
EndpointPermissionAuthorizer authorizer =
    invocation -> hostAuthorization.authorize(invocation);

EndpointPermissionInterceptor interceptor =
    new EndpointPermissionInterceptor(
        new DefaultEndpointPermissionResolver(),
        authorizer);
```

For manual annotation-only integration, register the interceptor once in Spring MVC configuration.
Controllers, services, and mappers never call Forga authorization methods explicitly. Collection
authorization remains in MyBatis query constraints so filtering, sorting, and pagination happen in
SQL.

## Authentication Adapters

Authentication frameworks provide identity only. Forga remains the decision point for RBAC, ABAC,
and ReBAC, so Sa-Token permission lists and Spring Security granted authorities are not consumed as
business authorization decisions.

For Sa-Token, add `forga-sa-token` and expose the selected `StpLogic` as a bean. For Spring
Security, add `forga-spring-security`. Both adapters map the authenticated identity to
`SubjectRef("user", loginId)`; there is no subject-type configuration.

The starter discovers `AuthenticatedSubjectProvider` beans without an adapter-selection property.
Enabled Forga integration requires exactly one provider and refuses to start when none or multiple
providers exist. A host that supplies its own provider does not add either adapter module.

## MyBatis And Spring Integration

`forga-mybatis` can apply translated query constraints to configured MyBatis statements. The host
registers statement authorization metadata and supplies the current subject/request attributes.

Important integration behavior:

- Configured statements receive authorization constraints.
- Unconfigured statements are left unchanged.
- Disabled integration leaves ordinary business SQL unchanged.
- Missing subject or unsupported configured SQL fails closed.
- Only allowlisted fields are translated into SQL.

For effective data scopes that change by subject, declare a dynamic boundary and resolve one
concrete typed constraint per query:

```java
MyBatisStatementAuthorization statement =
    new MyBatisStatementAuthorization(
        "MeetingMapper.selectPage",
        MyBatisAuthorizationBoundary.dynamic("meeting-list"));

MyBatisAuthorizationBoundaryResolver boundaries =
    (statementId, declared, subject, attributes) ->
        hostDataScopes.constraint(declared.id(), subject, attributes);
```

The resolver must return a concrete boundary with the same id. Null, unresolved, or raw-SQL results
fail before database execution. Fixed boundaries continue to work without a resolver.

`forga-spring-boot-starter` assembles optional runtime components and MyBatis integration when
enabled. Applications still provide host-specific resolvers, authorization attributes, active-scope
providers, and statement mappings.

Enable Forga explicitly on the application composition root:

```java
import com.luokuiai.forga.spring.EnableForga;

@SpringBootApplication
@EnableForga
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
```

An enabled Spring application can start before its authorization model is ready. Without a
`CompiledPolicy` Bean, the Starter logs a warning and does not create an
`AuthorizationEvaluator`; unrelated application endpoints continue to use their existing behavior.
This state is deliberately not represented by an allow-all evaluator. Any component that explicitly
requires an `AuthorizationEvaluator` still fails Spring dependency validation.

When the host provides one `CompiledPolicy` Bean and its specialized `Resolver` Beans, the Starter
automatically assembles:

- `ResolverRegistry`
- `RelationshipLookup`, `ObjectListingLookup`, and `AttributeLookup`
- `EvaluationLimits.defaults()`
- `AuthorizationEvaluator`

`PolicyDefinition` contains the host's permission expressions. `PolicyCompiler` validates their
structure and produces the immutable `CompiledPolicy`. The Starter derives required relations,
caveats, attributes, and grants from that policy and validates them against actual registered runtime
components, so the host does not maintain a duplicate capability list. Because policy and host data
are business-owned, the Starter cannot invent these beans.
An application may keep `@EnableForga` during incremental integration and add the policy later, or
omit `@EnableForga` to disable all Forga runtime components.

`CompiledPolicy` is currently an immutable startup snapshot of permission expressions. Dynamic
role grants, data-scope grants, relationships, and attributes do not require recompiling it: host
lookup and boundary resolver Beans read that business-owned data at evaluation time. Replacing the
permission expression model itself at runtime is not yet supported and requires an application
restart with a newly compiled policy.

### Runnable Spring Boot Example

[`forga-spring-boot-example`](forga-spring-boot-example/) is a complete, tested application showing:

- `@EnableForga` on the application composition root
- a `CompiledPolicy` Bean defining `view_document = viewer OR grant()`
- a forward-only `ForwardRelationshipResolver` Bean
- a persistence-shaped, mutable host store for role assignments, permission grants, and data scopes
- a batched `PermissionGrantLookup` reading one immutable host snapshot per invocation
- a `MyBatisAuthorizationBoundaryResolver` translating effective data scopes to typed constraints
- distinct tenant-scoped `user` and `membership` subjects for concurrent appointments
- a dynamic `appointed` relationship plus current-department validation for `DEPARTMENT` scope
- request providers reading subject type, subject id, effective tenant, and current department headers
- a controller mapping `/documents/{id}` to an authorization `ObjectRef`

Run it from the repository root:

```bash
./gradlew :forga-spring-boot-example:bootRun
```

The example grants `alice` access to `document:doc-1` through a relationship and grants `carol`
`view_document` through mutable role authorization data:

```bash
curl -i -H 'X-Subject-Id: alice' http://localhost:8080/documents/doc-1
curl -i -H 'X-Subject-Id: carol' \
  -H 'X-Effective-Tenant-Id: tenant-home' \
  http://localhost:8080/documents/doc-2
curl -i -H 'X-Subject-Type: membership' \
  -H 'X-Subject-Id: carol-target' \
  -H 'X-Effective-Tenant-Id: tenant-target' \
  -H 'X-Department-Id: department-a' \
  http://localhost:8080/documents/doc-2
curl -i -H 'X-Subject-Id: bob' http://localhost:8080/documents/doc-1
```

The membership request uses only roles granted to `membership:carol-target` in `tenant-target`.
It does not inherit `user:carol` roles from `tenant-home`. The same host snapshot also owns two
active department appointments; selecting an unrelated or deactivated department makes the
department boundary fail closed.

The header provider is intentionally limited to the example. Production applications should use
the Sa-Token adapter, Spring Security adapter, or a host authentication integration.
The example store is intentionally in-memory so it runs without infrastructure; production hosts
adapt the same lookup boundaries to their own repositories and cache invalidation strategy.

Each default uses `@ConditionalOnMissingBean`, so hosts can replace individual lookups, limits, or
the evaluator. A host `CaveatEvaluator` Bean is applied automatically when present. Spring Web
endpoint-to-object mapping remains host-owned through `EndpointPermissionAuthorizer`, which can
inject the assembled evaluator.

`forga.enabled` is not a supported configuration property. Environment properties cannot enable or
disable Forga integration.

Snapshot migration: `ForgaSubjectProvider` and `ForgaRequestAttributesProvider` moved to the core
`AuthenticatedSubjectProvider` and `AuthorizationAttributesProvider` contracts. `@RequiresResource`
and `ResourceAuthorizationService` were removed; use `@RequiresPermission`, `@PermitAll`, or a host
endpoint resolver. Property-based enablement was removed; use `@EnableForga` instead.

## Consistency And Limits

Each evaluation can carry one opaque consistency token. Resolvers may establish the token on the
first read; conflicting tokens fail closed.

Evaluation and listing enforce configured bounds:

- max depth
- max visited nodes
- max resolver calls
- max intermediate results
- page size
- batch size
- per-evaluation timeout and propagated resolver deadline
- cycle detection

## Disabled Behavior

The starter is opt-in. Without `@EnableForga`, it assembles no runtime components and the MyBatis
applicator returns the original SQL unchanged, with no required authorization request context.

## Build

```bash
./gradlew clean check
```

GitHub Actions runs the same Gradle check on pull requests targeting `main` or `develop`.

## Publishing

Every submodule is configured as a Maven publication under group `com.luokuiai.forga`. The default
version is `1.0.0-SNAPSHOT`; release jobs can override it with `-PreleaseVersion=...`.

Publish to the local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Maven Central publishing is handled by `.github/workflows/publish.yml` using the same Vanniktech
publishing plugin setup as the Liquibase adapter modules:

- pushes to `develop` publish a unique snapshot version;
- tags matching `vX.Y.Z` publish and release version `X.Y.Z`.

The underlying release command is:

```bash
./gradlew publishAndReleaseToMavenCentral -PreleaseVersion=1.0.0
```

Signing and Maven Central credentials are read by the publishing plugin from Gradle properties or
environment variables. The workflow maps them from `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, and `SIGNING_PASSWORD` repository secrets.
