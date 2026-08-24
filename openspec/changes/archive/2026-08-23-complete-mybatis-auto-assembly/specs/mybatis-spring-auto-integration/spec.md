## MODIFIED Requirements

### Requirement: Conditional Spring registration
The Spring integration MUST register MyBatis authorization components only when a host composition
root explicitly declares `@EnableForga` and MyBatis is present. Environment properties MUST NOT
alter this registration decision.

#### Scenario: Integration disabled
- **WHEN** the Starter is present without `@EnableForga`
- **THEN** no MyBatis authorization interceptor is registered and no request context is required

#### Scenario: Legacy property is present
- **WHEN** `forga.enabled=true` is configured without `@EnableForga`
- **THEN** no MyBatis authorization interceptor is registered

#### Scenario: Integration explicitly enabled
- **WHEN** a host composition root declares `@EnableForga`, MyBatis is present, and an authentication
  provider is available
- **THEN** the Forga MyBatis authorization interceptor is registered
- **AND** registration does not require a separate integration properties bean

#### Scenario: MyBatis is absent
- **WHEN** a host composition root declares `@EnableForga` but MyBatis is not on the classpath
- **THEN** no Forga MyBatis infrastructure is registered

## ADDED Requirements

### Requirement: Starter-managed MyBatis assembly
The Spring integration MUST assemble derivable MyBatis infrastructure from typed host declarations
and MUST preserve explicit host overrides.

#### Scenario: Typed declarations are present
- **WHEN** a host declares `MyBatisStatementAuthorization` and `MyBatisResourceMapping` beans
- **THEN** the starter aggregates them into the default statement registry and interceptor
- **AND** no raw resource-mapping `Map` bean is required

#### Scenario: No request attributes provider is declared
- **WHEN** a host does not declare an `AuthorizationAttributesProvider`
- **THEN** the starter registers an empty attributes provider

#### Scenario: No statement declarations are present
- **WHEN** MyBatis integration is enabled without statement authorization declarations
- **THEN** the starter registers an empty statement registry
- **AND** unconfigured statements remain unchanged

#### Scenario: Host override is present
- **WHEN** a host declares a registry, attributes provider, or Forga MyBatis interceptor bean
- **THEN** the corresponding starter default backs off

#### Scenario: Duplicate resource declarations are present
- **WHEN** multiple resource mapping beans declare the same query resource
- **THEN** application startup fails with a configuration error identifying the duplicate resource

### Requirement: Explicit authenticated subject ownership
The Spring integration MUST require exactly one authenticated subject provider and MUST NOT create a
default identity.

#### Scenario: Authentication provider is absent
- **WHEN** Forga is explicitly enabled without an authentication provider
- **THEN** application startup fails with the authentication-provider validation error
