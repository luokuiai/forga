## ADDED Requirements

### Requirement: Total enable switch for authentication adapters
Optional Spring Boot authentication adapters MUST NOT create authenticated-subject provider beans when `@EnableForga` is absent. When it is present, each available adapter SHALL retain its existing mapping behavior, and multiple providers SHALL still fail startup.

#### Scenario: Annotation is absent
- **WHEN** Sa-Token or Spring Security is present but `@EnableForga` is absent
- **THEN** no Forga adapter provider is auto-configured

#### Scenario: Annotation is present
- **WHEN** `@EnableForga` is present with one supported authentication framework
- **THEN** its provider is available to Forga integration
