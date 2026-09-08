## MODIFIED Requirements

### Requirement: Registry-based MVC auto-configuration
The Spring Boot Starter SHALL register endpoint enforcement automatically when Forga is enabled and
an endpoint authorizer is available. Endpoint contributors SHALL be optional metadata sources and
SHALL NOT be required for annotation-based enforcement.

#### Scenario: Annotation-only integration is enabled
- **WHEN** a host provides an endpoint authorizer without endpoint contributors
- **THEN** the Starter registers one MVC interceptor using annotation and host-resolver metadata

#### Scenario: Registry integration is enabled
- **WHEN** a host provides an endpoint contributor and authorizer
- **THEN** the Starter registers one MVC interceptor using the composed endpoint metadata

#### Scenario: Registry enforcement is incomplete
- **WHEN** a host provides an endpoint contributor without an authorizer
- **THEN** application startup fails before registered endpoints can receive requests

#### Scenario: Annotation-only endpoint is unresolved
- **WHEN** automatic enforcement encounters a handler without required-permission or explicit
  permit-all metadata
- **THEN** invocation is rejected before the handler body executes

#### Scenario: Forga is disabled
- **WHEN** Forga integration is disabled
- **THEN** the Starter does not assemble registrations or add endpoint enforcement
