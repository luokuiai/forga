## Context

`forga-scope` deliberately provides an opinionated boundary model for common tenant, organization, department, position, and workspace integrations. The host still owns those records and their semantics. Today the service performs separate evaluator calls around single-object host callbacks. Those callbacks neither batch nor participate in the evaluator consistency token, and `ScopeSwitchRequest.permission` can disagree with the service's configured entry permission.

## Goals / Non-Goals

**Goals:**

- Preserve the business-friendly scope module and its active/acting context.
- Make single and bulk scoped checks semantically equivalent and set-oriented.
- Keep host boundary reads set-oriented and suitable for one host transaction or snapshot.
- Return a stable phase for denials so hosts can audit which part of the composed check failed.
- Use one configured permission for every scope-entry check.

**Non-Goals:**

- Add tenant, organization, position, or concurrent-appointment types to Core.
- Define the host meaning or persistence of membership, assignment, ownership, or cross-scope grants.
- Perform per-row checks for collection filtering.

## Decisions

### Scope remains an opinionated optional module

`ScopeRef`, `ActiveScope`, acting context, membership/assignment templates, and query helpers remain in `forga-scope`. Core receives only ordinary opaque check requests and an evaluation read context. This keeps the quick integration vocabulary without contaminating the domain-neutral engine.

### Host boundary reads become complete batch lookups

`ObjectScopeResolver` becomes `ObjectScopeLookup` over distinct object references. `CrossScopeAccessResolver` becomes `CrossScopeGrantLookup` over complete cross-scope requests. Both return complete immutable maps with exactly one result for every request. The service rejects missing, extra, or null results and fails closed. Implementations perform these reads inside the host's request transaction or equivalent snapshot boundary.

This is a direct breaking replacement because the SDK is still under development. Default denial remains available for cross-scope grants.

### Scoped checks use staged batches

The service bulk-evaluates distinct scope-entry checks first. It then performs one ownership batch and at most one cross-scope grant batch for requests whose entry checks passed, followed by one bulk object-permission evaluation. This preserves fail-fast behavior without adding a new evaluator-session abstraction to Core. Storage-level snapshot consistency remains a host transaction concern, as it is for composed query constraints.

### The configured entry permission is authoritative

`ScopeSwitchRequest` no longer carries a permission. Both `canSwitch` and scoped object checks use the service's configured entry permission. This removes an accidental second configuration path.

### Decisions identify the failed phase

`ScopedPermissionDecision` carries a `ScopeAuthorizationPhase` in addition to the final `CheckDecision`. Phases distinguish active-scope validation, scope entry, object ownership, cross-scope grant, and object permission. This is integration-level explanation; Core decision reasons remain domain-neutral.

### Query filtering remains set-based

`ScopeQueryConstraints` continues to compose active-scope ownership with a host-provided grant constraint. The new bulk check is for bounded command/object batches, not a replacement for database filtering and pagination.

## Risks / Trade-offs

- [Breaking resolver signatures increase migration work] -> No compatibility layer is kept; README examples and tests show the direct batch implementation.
- [A host may return incomplete batch maps] -> Validate response cardinality and keys before using any result.
- [Sequential stages add latency] -> Each stage is set-oriented and only cross-scope requests invoke the grant lookup; core checks are bulk evaluated.
- [Independent host stores may observe different snapshots] -> Require implementations to run the composed call in one host transaction or equivalent request snapshot; do not add a Core session abstraction without a broader use case.

## Migration Plan

1. Replace object ownership lambdas with `ObjectScopeLookup` batch implementations.
2. Replace Boolean cross-scope callbacks with `CrossScopeGrantLookup` batch implementations.
3. Remove the permission argument from `ScopeSwitchRequest` construction.
4. Consume `ScopedPermissionDecision.phase()` for audit and denial mapping where required.
5. Run focused scope/core tests and the full Gradle check.

## Open Questions

None. Host-specific named scope factories can be added independently when a concrete integration requires them; they are not necessary for authorization correctness in this change.
