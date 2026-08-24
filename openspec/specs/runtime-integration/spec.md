# runtime-integration Specification

## Purpose
TBD - created by archiving change build-embedded-authorization-engine. Update Purpose after archive.
## Requirements
### Requirement: Opt-in runtime assembly
Framework integrations MUST register authorization components only when a host composition root
explicitly declares `@EnableForga`. Environment properties, including `forga.enabled`, MUST NOT
enable or disable Forga assembly. Annotation-enabled integration MUST NOT require a separate runtime
properties bean carrying another enablement flag. Enabled integration MUST validate policies,
resolver capabilities, authentication providers, and duplicate registrations before serving
requests.

#### Scenario: Composition root enables Forga
- **WHEN** a host configuration class declares `@EnableForga`
- **THEN** Forga integration components and the versioned startup banner are registered
- **AND** incomplete required infrastructure causes application startup to fail with a precise
  configuration error
- **AND** no separate integration enablement properties bean is required

#### Scenario: Legacy property attempts to enable Forga
- **WHEN** the Starter is present and `forga.enabled=true` is configured without `@EnableForga`
- **THEN** Forga integration components and the startup banner MUST remain absent

### Requirement: Integration boundaries preserve core neutrality
Runtime integrations MUST keep host-domain semantics in host adapters, policies, resolver
implementations, and query mappings. They MUST NOT require Forga core or public API types to expose
tenant, user, person, organization, workflow, meeting, todo, appointment, proxy, mapped, or borrowed
concepts.

#### Scenario: Host registers a tenant-aware resolver
- **WHEN** a host resolver uses tenant or organization data to answer relationship or attribute
  requests
- **THEN** the tenant-aware logic remains inside the resolver or host policy
- **AND** Forga core receives only opaque subjects, objects, relations, attributes, and structured
  resolver results

### Requirement: Disabled behavior preserves business queries
When no host composition root declares `@EnableForga`, integrations MUST NOT install authorization
interceptors, mutate query context, alter SQL produced by business persistence code, or emit an
enabled Forga startup banner.

#### Scenario: Unannotated application runs a list query
- **WHEN** the Starter is present without `@EnableForga` and a business Mapper executes its normal
  query
- **THEN** the query executes without a Forga predicate, join, or required context

### Requirement: MyBatis constraint application
The MyBatis integration MUST apply at most one composed, parameterized authorization constraint at
a declared query boundary and MUST reject unsupported query shapes while enabled.

#### Scenario: Supported Mapper query is authorized
- **WHEN** an enabled mapped query declares its authorization resource and registered field mapping
- **THEN** the adapter applies one set-oriented constraint without per-result checks

### Requirement: Request-scoped evaluation state
Runtime integrations MUST isolate subject, request attributes, consistency context, caches, and
deadlines by request and MUST clean them after synchronous or exceptional completion.

#### Scenario: Authorized request throws an exception
- **WHEN** downstream business code fails after authorization begins
- **THEN** Forga clears all request-scoped state before the execution thread is reused

### Requirement: Integration rollback
Hosts MUST be able to remove `@EnableForga`, restart, and return to their original query and
authorization path without a Forga data migration.

#### Scenario: Host rolls back integration
- **WHEN** `@EnableForga` is removed from the composition root and the application restarts
- **THEN** Forga integration components are absent and no relationship data rollback is required

### Requirement: Automatic authorization evaluator assembly
When a host explicitly enables Forga, the Spring integration MUST assemble an
`AuthorizationEvaluator` from one host-owned `CompiledPolicy`, registered relationship resolvers,
and conservative default evaluation limits. Hosts MUST be able to replace the evaluator, lookup
adapters, resolver registry, caveat evaluator, and limits with their own Beans. Check-only assembly
MUST NOT require reverse resolver capabilities.

#### Scenario: Complete host runtime is enabled
- **WHEN** a host declares `@EnableForga`, one compiled policy, and forward resolvers supporting
  every relation required by that policy
- **THEN** exactly one `AuthorizationEvaluator` Bean is registered
- **AND** authorization checks use the host policy and resolver data

#### Scenario: Check-only host omits reverse capability
- **WHEN** an enabled host provides every required forward resolver but no reverse resolver
- **THEN** evaluator startup succeeds and `check` remains available
- **AND** a later `listObjects` call fails closed if its reverse capability is unavailable

#### Scenario: Host overrides the evaluator
- **WHEN** an enabled host provides its own `AuthorizationEvaluator` Bean
- **THEN** the Starter backs off and does not register another evaluator

#### Scenario: Enabled runtime lacks policy or forward resolver capability
- **WHEN** an enabled host omits its compiled policy or a forward resolver required by that policy
- **THEN** application startup fails before requests are served with a precise configuration error

#### Scenario: Integration is not enabled
- **WHEN** the Starter is present without `@EnableForga`
- **THEN** no evaluator, registry adapter, or evaluation limits Bean is registered by Forga

### Requirement: Resolver registry evaluation bridge
The runtime integration MUST adapt forward and reverse resolver batches to evaluator lookup
contracts without per-result queries, MUST preserve reverse pagination and consistency state, and
MUST fail closed when capabilities or resolver responses are incomplete or malformed.

#### Scenario: Forward requests span resolver capabilities
- **WHEN** one evaluator lookup batch contains requests handled by multiple registered resolvers
- **THEN** requests are grouped by resolver and each resolver receives one bounded batch
- **AND** every returned direct subject and subject set is converted to an evaluator relationship
  entry

#### Scenario: Reverse resolver returns a continuation
- **WHEN** a reverse resolver returns a bounded object page with a cursor and consistency token
- **THEN** the evaluator listing page contains the same objects, cursor, and consistency token

#### Scenario: Resolver response is malformed
- **WHEN** a resolver returns a missing, duplicate, extra, null, or mismatched batch response
- **THEN** the lookup raises a structured resolver failure and authorization fails closed

### Requirement: Host-owned endpoint authorization mapping
Automatic evaluator assembly MUST NOT infer protected objects from Spring endpoint metadata. Hosts
MUST continue to provide endpoint authorization mapping when endpoint permissions are enabled.

#### Scenario: Host enables endpoint permissions
- **WHEN** endpoint permission metadata is registered
- **THEN** the host provides an `EndpointPermissionAuthorizer` that may inject the assembled
  evaluator
- **AND** the Starter does not guess an object reference from route or handler data

### Requirement: Runtime APIs correspond to assembled behavior
The Spring integration MUST expose only runtime types used by production assembly and MUST NOT
publish standalone request-scope or component-wrapper APIs that no integration consumes.

#### Scenario: Runtime beans are assembled
- **WHEN** Forga is enabled
- **THEN** hosts consume the typed policy, registry, lookups, limits, and evaluator beans directly

### Requirement: Focused authentication provider selection
MyBatis assembly MUST select exactly one authenticated subject provider and MUST report a focused
Forga configuration error when provider ownership is absent or ambiguous.

#### Scenario: Multiple providers exist
- **WHEN** an enabled MyBatis integration discovers more than one authenticated subject provider
- **THEN** startup fails with the Forga exactly-one-provider validation error
