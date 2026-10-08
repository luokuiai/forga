## Why

Web permission enforcement currently depends on an optional authorizer bean and exposes a public interceptor that hosts can register manually. This makes `@EnableForga` an unreliable switch and allows routes to bypass the intended fail-closed policy. The first release is not yet published, so the integration contract can be corrected now.

## What Changes

- Automatically own and register the MVC interceptor when Forga Web is enabled, independently of endpoint contributors.
- Require an endpoint authorizer at startup whenever `@EnableForga` activates servlet MVC integration.
- Expose a code-configured Web scope bean for include/exclude paths; validate malformed or ambiguous scope and declarations outside it.
- **BREAKING** Remove the public `EndpointPermissionInterceptor` construction API. Hosts configure scope, not interceptor registration.
- Gate optional identity-adapter auto-configuration behind `@EnableForga`.
- Update documentation and tests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `endpoint-permission-resolution`: Web assembly, scope, disabled behavior, and startup validation.
- `authentication-subject-adapters`: Adapter activation follows the total enable switch.

## Impact

Spring Boot Starter, Spring Web support, Sa-Token and Spring Security adapters, README, and tests. Host applications retain ownership of identities and authorization relationships; Forga core does not change.

## Non-goals

No Spring Security enforcement dependency, no new database integration, and no change to the host authorizer contract.
