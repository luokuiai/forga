## MODIFIED Requirements

### Requirement: Registry-based MVC auto-configuration
The Spring Boot Starter SHALL register exactly one endpoint-enforcement interceptor whenever `@EnableForga` is present in a servlet MVC application. This SHALL NOT depend on an endpoint contributor. The Starter MUST require an endpoint authorizer at startup and MUST NOT expose manual interceptor construction as a supported host API.

#### Scenario: Annotation-only integration is enabled
- **WHEN** an enabled host supplies an authorizer and annotated controllers but no endpoint contributor
- **THEN** the Starter registers enforcement and checks annotated endpoints

#### Scenario: Registry integration is enabled
- **WHEN** an enabled host supplies an endpoint contributor and authorizer
- **THEN** the Starter registers one MVC interceptor using composed endpoint metadata

#### Scenario: Registry enforcement is incomplete
- **WHEN** an enabled host supplies an endpoint contributor without an authorizer
- **THEN** application startup fails before registered endpoints can receive requests

#### Scenario: Authorizer is missing
- **WHEN** `@EnableForga` activates servlet MVC integration without an endpoint authorizer
- **THEN** startup fails before requests are served

#### Scenario: Forga is disabled
- **WHEN** `@EnableForga` is absent
- **THEN** the Starter does not assemble registrations or add endpoint enforcement

## ADDED Requirements

### Requirement: Code-configured Web enforcement scope
The Starter SHALL accept one host-defined scope with include and exclude path patterns. When none is provided, it SHALL intercept all MVC handler paths. Only handler requests inside the effective scope SHALL undergo Forga endpoint authorization; an unresolved handler inside the scope MUST be denied. Empty, invalid, or ambiguous scope configuration MUST fail startup.

#### Scenario: API path is scoped
- **WHEN** a host includes `/api/**` and requests `/api/orders` or `/health`
- **THEN** the first request is enforced and the second is not inspected by Forga

#### Scenario: No scope is provided
- **WHEN** an enabled host provides no scope
- **THEN** every MVC handler request is subject to Forga endpoint authorization

#### Scenario: Multiple scopes are provided
- **WHEN** more than one scope bean is present
- **THEN** application startup fails

### Requirement: Declared permissions remain in enforcement scope
At startup, Forga MUST reject statically annotated or externally registered MVC handlers whose mapped paths are outside the effective Web scope. A handler with multiple mapped paths MUST have every path covered.

#### Scenario: Annotated handler is outside the scope
- **WHEN** a handler declares `@RequiresPermission` but its mapped path is excluded
- **THEN** application startup fails with the handler identification

#### Scenario: Contributor registration is outside the scope
- **WHEN** a contributor registers a handler whose mapped path is outside the scope
- **THEN** application startup fails with the handler identification
