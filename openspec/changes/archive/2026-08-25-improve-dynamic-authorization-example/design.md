## Context

`RelationshipResolver` models three independently declared capabilities, but every implementation
must currently implement all three methods. The runnable example also uses a fixed permission map,
which hides the intended boundary between immutable policy expressions and mutable host-owned role,
grant, and data-scope records.

## Goals / Non-Goals

**Goals:**

- Make forward-only resolver implementations concise without weakening complete-batch behavior.
- Show mutable host authorization records feeding batched grant checks and request-time query
  constraints.
- Prove that authorization data changes do not require policy recompilation.
- Keep all example lookups bounded and set-oriented.

**Non-Goals:**

- Defining role, grant, data-scope, cache, or persistence schemas in SDK modules.
- Loading object attributes implicitly in the evaluator.
- Adding a dynamic policy DSL or changing `CompiledPolicy` lifecycle semantics.
- Providing production cache invalidation in the example.

## Decisions

### Default undeclared resolver operations on the existing interface

`RelationshipResolver.resolveReverse` and `resolveAttributes` will become default methods that
return one empty response per submitted request while preserving request consistency. Registry
capability declarations continue to decide whether either operation is reachable in normal SDK
evaluation.

This is preferred over splitting the public interface because it is source- and binary-compatible,
does not change registry ownership, and removes the observed boilerplate. A separate support class
would introduce another API that hosts must discover without reducing the interface obligation.

### Persistence-shaped mutable data remains inside the example

The example will contain a thread-safe host data store with role assignments, permission grants,
and data-scope grants. The store represents records a real host would load from a database, but uses
in-memory collections so the example remains runnable without external infrastructure.

`PermissionGrantLookup` receives a bounded request batch and resolves it against one immutable store
snapshot. Mutations replace the snapshot atomically, modeling versioned host cache invalidation.

### Data scopes resolve to typed query constraints

The example will provide a `MyBatisAuthorizationBoundaryResolver` that reads the subject's effective
scope once per boundary resolution and returns an allowlisted, parameterized `QueryConstraint`.
It will not execute SQL or inspect rows individually. This keeps query execution and concrete
parameter binding host-owned while demonstrating the intended ABAC integration boundary.

The example scope vocabulary is `OWNER`, `DEPARTMENT`, and `TENANT`. Every scope remains bounded:
owner, department, and tenant grants map respectively to `owner_id`, `department_id`, and
`tenant_id` predicates rather than treating tenant scope as an unconstrained query.

### Tests exercise mutations through host APIs

Integration tests will mutate the example data store directly, then repeat authorization and
boundary resolution with the same `CompiledPolicy` bean. This demonstrates runtime behavior without
adding unauthenticated administration endpoints to the sample application.

### Concurrent appointments remain host-owned authorization data

The example will represent a concurrent appointment as a tenant-scoped relationship between a
`MEMBERSHIP` subject and a department object. Role assignments and grants remain keyed by the
effective tenant and exact subject, so a membership cannot inherit the source user's roles.

The current department and effective tenant will be request attributes. A department boundary is
resolved only when the selected department is an active appointment for that membership. This
demonstrates identity switching and request-scoped ABAC without introducing tenant, membership, or
department concepts into Forga core.

## Risks / Trade-offs

- [Default methods could hide an accidentally omitted capability implementation] -> The registry
  only routes capabilities declared by `ResolverDescriptor`; tests verify undeclared defaults are
  empty and declared capabilities still use host implementations.
- [An in-memory store may look production-ready] -> Documentation explicitly labels it as a
  persistence-shaped example and keeps cache and schema ownership with the host.
- [Example data-scope logic could become a generic rule engine] -> The sample supports a small fixed
  scope set and translates it directly to typed constraints.
- [A membership could accidentally inherit its source user's grants] -> The example stores grants
  by effective tenant and exact `SubjectRef`, and tests that user and membership subjects remain
  isolated.
