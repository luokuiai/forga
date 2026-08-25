## Context

`ForgaEvaluatorAutoConfiguration` currently requires a `CompiledPolicy` whenever `@EnableForga` is
present. This conflates installing the integration with activating evaluator-based authorization.
An empty policy cannot represent the intermediate state because `PolicyDefinition` intentionally
requires at least one permission.

## Goals / Non-Goals

**Goals:**

- Let hosts install and enable the Starter before defining an authorization model.
- Make the inactive evaluator state visible at startup.
- Preserve strict validation and fail-closed behavior for configured evaluators.

**Non-Goals:**

- Providing an implicit allow-all authorization decision.
- Making compiled policy definitions mutable or hot-reloadable.
- Changing dynamic relationship, attribute, query, or listing resolution.

## Decisions

The default evaluator bean is conditional on a `CompiledPolicy` bean. When no policy is present,
the Starter leaves `AuthorizationEvaluator` absent and logs one warning after singleton assembly.
Ordinary application behavior therefore remains unchanged, while any host component that explicitly
depends on an evaluator still fails through normal Spring dependency validation.

An implicit allow-all evaluator was rejected because it makes a missing security configuration
indistinguishable from a deliberate authorization grant. Allowing empty policy definitions was also
rejected because an empty model has no unambiguous decision semantics and would weaken core
validation.

When a policy is present, the existing evaluator path remains unchanged: resolver capabilities are
validated and evaluation failures remain closed. Host-owned resolver implementations continue to
read dynamic relationship and attribute data at evaluation time; no collection or traversal behavior
changes.

## Risks / Trade-offs

- [Risk] A host may overlook the warning and assume authorization is active. -> The evaluator bean
  remains absent, so any explicitly protected integration that requires it fails to assemble instead
  of silently granting access.
- [Risk] Conditional bean ordering could report a warning despite a host override. -> Emit the
  warning only when both `CompiledPolicy` and `AuthorizationEvaluator` are absent.
- [Trade-off] Runtime policy replacement remains unsupported. -> Treat it as a separate design that
  requires atomic versioning and cache consistency guarantees.

## Migration Plan

Hosts that currently provide a real policy need no changes. Hosts using an invalid empty placeholder
remove that bean and may add a real `CompiledPolicy` later. Removing `@EnableForga` remains the full
rollback path.

## Open Questions

None.
