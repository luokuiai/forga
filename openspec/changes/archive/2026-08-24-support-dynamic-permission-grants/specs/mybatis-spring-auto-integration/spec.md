## MODIFIED Requirements

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
