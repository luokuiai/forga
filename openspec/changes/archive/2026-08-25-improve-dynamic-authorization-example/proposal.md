## Why

Hosts that resolve only one relationship direction currently implement unrelated empty resolver
methods, while the runnable example does not show how stable permission definitions consume mutable
RBAC grants and ABAC-style data scopes. This obscures the intended low-intrusion integration model.

## What Changes

- Give undeclared reverse and attribute resolver capabilities complete, fail-closed default
  responses so forward-only hosts do not write empty boilerplate.
- Expand the runnable Spring Boot example with host-owned mutable role grants and data-scope rules.
- Demonstrate that authorization data changes are observed without rebuilding `CompiledPolicy`.
- Demonstrate request-time data-scope translation into a typed MyBatis query constraint.
- Demonstrate tenant-scoped `USER` and `MEMBERSHIP` subjects, including concurrent appointment
  relationships and selection of the current department for data-scope enforcement.
- Clarify the distinction between stable policy expressions and dynamic host authorization data.
- Keep persistence schemas, caching, invalidation, business relations, and attribute semantics owned
  by host applications.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `core-resolver-contracts`: Undeclared optional resolver operations have complete fail-closed
  defaults that reduce host implementation boilerplate.
- `spring-boot-example`: The runnable example demonstrates mutable host-owned permission grants and
  data scopes without dynamic policy recompilation.

## Impact

- `forga-core`: backward-compatible public default methods on `RelationshipResolver` plus tests.
- `forga-spring-boot-example`: host authorization data store, adapters, endpoints, and integration
  tests for dynamic grants, data scopes, and concurrent appointments.
- `README.md`: focused guidance for stable policy models and dynamic authorization data.
- No new runtime dependencies, persistence ownership, or breaking API changes.
