## Why

Hosts often add the Forga Starter before they have an authorization model ready. Requiring a
`CompiledPolicy` at that stage prevents otherwise unrelated application endpoints from starting and
encourages unsafe placeholder policies.

## What Changes

- Allow an `@EnableForga` application to start without a `CompiledPolicy`.
- Skip automatic `AuthorizationEvaluator` assembly while no policy exists and emit one clear
  startup warning that evaluator-based authorization is inactive.
- Preserve policy validation, resolver validation, host overrides, and fail-closed evaluation once
  a policy is supplied.
- Keep empty `PolicyDefinition` instances invalid; unconfigured startup is represented by absence
  of a policy, not by an allow-all policy.
- Dynamic policy replacement is explicitly outside this change; host relationship and attribute
  data remain dynamically resolved through host-owned resolvers.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `runtime-integration`: distinguish an enabled but unconfigured Starter from an active evaluator
  runtime.

## Impact

- `forga-spring-boot-starter` evaluator auto-configuration and tests.
- README integration guidance and the Spring Boot example documentation.
- No core policy semantics, resolver ownership, or public API signatures change.
