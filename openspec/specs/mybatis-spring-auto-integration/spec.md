# mybatis-spring-auto-integration Specification
## Purpose
TBD - created by archiving change add-mybatis-spring-auto-integration. Update Purpose after archive.
## Requirements
### Requirement: Statement metadata driven authorization
The integration MUST apply authorization only at statement ids explicitly registered with a fixed or
dynamic typed authorization boundary. Statement declarations MUST contain only metadata consumed by
enforcement, and every dynamic declaration MUST resolve to one concrete typed boundary before SQL
translation.

#### Scenario: Configured fixed statement is authorized
- **WHEN** a configured MyBatis statement with a fixed boundary executes while Forga is enabled
- **THEN** the integration applies its composed typed authorization constraint

#### Scenario: Configured dynamic statement is authorized
- **WHEN** a configured MyBatis statement with a dynamic boundary executes while Forga is enabled
- **THEN** the host resolver receives the declaration, current subject, and request attributes once
- **AND** the resolved typed constraint is applied to the statement

#### Scenario: Unconfigured statement executes
- **WHEN** a statement id has no authorization metadata
- **THEN** the integration leaves the SQL unchanged

### Requirement: Generic request providers
The integration MUST obtain the current subject and request attributes through neutral provider
interfaces and MUST NOT define host-domain authentication context.

#### Scenario: Subject is available
- **WHEN** a configured statement executes while a subject provider returns a subject
- **THEN** authorization proceeds using neutral SDK references

#### Scenario: Subject is missing
- **WHEN** a configured statement executes while Forga is enabled and no subject is available
- **THEN** the integration fails closed before SQL execution

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

### Requirement: Starter-managed MyBatis assembly
The Spring integration MUST assemble derivable MyBatis infrastructure from typed host declarations,
MUST use a host `MyBatisAuthorizationBoundaryResolver` when supplied, and MUST preserve explicit
host overrides.

#### Scenario: Typed declarations are present
- **WHEN** a host declares `MyBatisStatementAuthorization` and `MyBatisResourceMapping` beans
- **THEN** the starter aggregates them into the default statement registry and interceptor
- **AND** no raw resource-mapping `Map` bean is required

#### Scenario: Dynamic resolver is present
- **WHEN** a host declares a `MyBatisAuthorizationBoundaryResolver` Bean
- **THEN** the default interceptor uses it for configured statement boundaries

#### Scenario: No dynamic resolver is present
- **WHEN** a host uses only fixed boundaries and declares no boundary resolver
- **THEN** the starter preserves each declared fixed boundary unchanged

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

### Requirement: Safe SQL rewriting
The MyBatis integration MUST apply at most one translated authorization constraint to the exact
`BoundSql` instance executed for a supported SELECT and MUST reject unsupported SQL while enabled.

#### Scenario: Supported select query
- **WHEN** a configured SELECT statement executes
- **THEN** the database receives one parameterized authorization constraint on that query

#### Scenario: Query contains trailing clauses
- **WHEN** a configured SELECT contains top-level ordering or pagination clauses
- **THEN** the authorization predicate is inserted at the correct SELECT AST location

#### Scenario: Unsupported query shape
- **WHEN** a configured non-SELECT or unsupported SELECT statement executes while enabled
- **THEN** the integration fails closed before SQL execution

#### Scenario: Integration is disabled
- **WHEN** a statement executes while Forga integration is disabled
- **THEN** the executable SQL and its parameters remain unchanged
