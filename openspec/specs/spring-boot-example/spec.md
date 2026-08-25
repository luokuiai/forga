# spring-boot-example Specification

## Purpose
TBD - created by archiving change add-spring-boot-example. Update Purpose after archive.
## Requirements
### Requirement: Runnable Spring Boot authorization example
The repository MUST provide a runnable, non-published Spring Boot example that enables Forga and
declares the host-owned compiled policy, relationship resolver, and authenticated subject provider
required by starter auto-configuration.

#### Scenario: Example application starts
- **WHEN** the example application context starts
- **THEN** Spring assembles an `AuthorizationEvaluator` without missing-bean failures

#### Scenario: Declared relationship allows access
- **WHEN** the configured example subject requests the configured document
- **THEN** the protected endpoint returns the document response

#### Scenario: Missing identity is rejected
- **WHEN** a request does not provide the example identity header
- **THEN** the protected endpoint returns an unauthenticated response

#### Scenario: Missing relationship denies access
- **WHEN** an authenticated subject lacks the required relationship
- **THEN** the protected endpoint returns a forbidden response

### Requirement: Discoverable host configuration guidance
The README MUST link to the runnable example and MUST explain that `@EnableForga` requires a
host-owned `CompiledPolicy` and matching resolver beans, while applications that do not enable Forga
do not need those beans.

#### Scenario: User encounters a missing policy bean
- **WHEN** a user consults the Spring integration documentation
- **THEN** the documentation identifies the required bean, its purpose, and the runnable example

### Requirement: Example demonstrates dynamic host authorization data
The runnable example MUST model mutable host-owned role assignments, permission grants, and data
scope grants while keeping the compiled permission expression immutable.

#### Scenario: Role permission changes at runtime
- **WHEN** the example host adds or removes a role permission grant
- **THEN** a later authorization check observes the new grant result
- **AND** the application continues using the same `CompiledPolicy` instance

#### Scenario: Grant lookup evaluates a batch
- **WHEN** multiple authorization checks require dynamic grants
- **THEN** the example lookup resolves the submitted requests against one host data snapshot
- **AND** returns one result for every request without enumerating all application objects

### Requirement: Example demonstrates dynamic data-scope constraints
The runnable example MUST translate a host-owned effective data scope into an allowlisted,
parameterized query constraint at request time. The example SHALL use `OWNER`, `DEPARTMENT`, and
`TENANT` scopes and SHALL preserve an explicit query boundary for every scope.

#### Scenario: Effective scope changes
- **WHEN** the example host changes a subject's data-scope grant
- **THEN** a later dynamic boundary resolution returns the constraint for the new scope
- **AND** policy recompilation is not required

#### Scenario: Tenant scope remains bounded
- **WHEN** a subject receives the `TENANT` data scope
- **THEN** dynamic boundary resolution returns a tenant-id predicate
- **AND** does not produce an unconstrained query

#### Scenario: Data scope is missing
- **WHEN** a subject has no effective data-scope grant for the declared boundary
- **THEN** dynamic boundary resolution fails closed without producing an unbounded query

### Requirement: Example demonstrates concurrent appointment authorization
The runnable example MUST model ordinary users and concurrent-appointment memberships as distinct,
tenant-scoped authorization subjects. Membership grants MUST remain independent from grants held by
the source user, and department data scope MUST be restricted to an active department relationship
selected for the current request.

#### Scenario: Membership uses target-tenant grants
- **WHEN** a membership subject is checked in its effective target tenant
- **THEN** the grant lookup uses roles assigned to that exact membership in that tenant
- **AND** does not inherit roles assigned to the ordinary user or another tenant

#### Scenario: Membership enters an appointed department
- **WHEN** an active membership is related to a target-tenant department
- **THEN** relationship evaluation allows the membership to enter that department
- **AND** deactivating the appointment makes a later check fail closed

#### Scenario: Current department scopes a query
- **WHEN** a membership has `DEPARTMENT` data scope and selects an active appointed department
- **THEN** the boundary resolver returns a parameterized `department_id` constraint
- **AND** selecting an unrelated or inactive department fails closed
