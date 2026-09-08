## Why

Annotation-only Spring MVC hosts currently receive no automatic endpoint enforcement unless they
also declare an unrelated `EndpointPermissionContributor` bean. This conflates optional metadata
for unmodifiable controllers with activation of the common Web enforcement pipeline and encourages
meaningless empty contributors.

## What Changes

- Activate Spring MVC endpoint permission assembly independently of contributor presence.
- Treat endpoint contributors as an optional metadata source while continuing to compose
  annotations, external registrations, and host resolvers.
- Automatically install the framework-owned endpoint interceptor when a host authorizer is
  available.
- Stop treating a host-provided interceptor as an enforcement extension point or as a substitute
  for an endpoint authorizer.
- Preserve fail-closed behavior: every intercepted handler must resolve to required-permission or
  explicit permit-all metadata.
- Update Starter tests and documentation for annotation-only automatic enforcement.
- Non-goal: change permission evaluation, endpoint metadata conflict handling, or host ownership of
  authorization data.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `endpoint-permission-resolution`: Decouple automatic MVC enforcement from external endpoint
  contributors while retaining explicit metadata and fail-closed requirements.

## Impact

- `forga-spring-boot-starter`: automatic configuration conditions and assembly inputs.
- `forga-spring-boot-starter` tests: annotation-only, contributor, and incomplete
  configuration cases.
- Spring Web integration documentation and endpoint-permission-resolution specification.
- No public Java API or dependency changes.
