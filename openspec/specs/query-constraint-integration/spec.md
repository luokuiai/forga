# query-constraint-integration Specification

## Purpose
TBD - created by archiving change build-embedded-authorization-engine. Update Purpose after archive.
## Requirements
### Requirement: Typed query constraints
Forga MUST represent authorization query filters as typed fields, parameters, predicates, joins,
existence checks, boolean composition, and correlations rather than raw SQL fragments.

#### Scenario: Policy produces a correlated existence constraint
- **WHEN** a business policy requires a related membership row
- **THEN** Forga produces a typed correlated existence node with bound parameters

### Requirement: Business-owned query execution
The host persistence layer MUST remain responsible for querying business tables, and applying a
constraint MUST NOT require Forga columns or changes to business entity schemas.

#### Scenario: Forga is removed from a query
- **WHEN** the authorization constraint is not applied
- **THEN** the original business query remains structurally executable against the unchanged schema

### Requirement: Safe adapter translation
Persistence adapters MUST use allowlisted field mappings and parameter binding, MUST transform the
executable query through a syntax-aware representation, and MUST reject unknown fields, operators,
aliases, unsupported constraint nodes, or unsupported query shapes before execution.

#### Scenario: Unknown query field is supplied
- **WHEN** a constraint refers to a field not registered for that business query
- **THEN** translation fails closed before SQL execution

#### Scenario: Clause text appears inside a nested query or literal
- **WHEN** an adapter applies a constraint to a supported SELECT containing nested clause text
- **THEN** it modifies only the intended top-level SELECT structure

#### Scenario: Unsupported parameter operand is supplied
- **WHEN** a constraint uses an operator whose typed operand cannot be bound safely
- **THEN** translation fails closed before SQL execution

### Requirement: Set-oriented constraint generation
Constraint generation MUST produce set-oriented filters and MUST NOT require per-row authorization
queries.

#### Scenario: Business list query is authorized
- **WHEN** a host applies one generated constraint to a paginated business query
- **THEN** authorization is evaluated within the query without one resolver or SQL call per row

### Requirement: Constraint observability
Constraint generation and translation MUST expose timing, selected policy, constraint node count,
and rejection reason while omitting parameter values from logs by default.

#### Scenario: Constraint translation fails
- **WHEN** an adapter rejects a constraint node
- **THEN** a structured reason and safe metadata are observable without logging bound values

### Requirement: Request-time typed constraint resolution
Forga MUST allow a host to select a complete typed query constraint at request time from neutral
statement metadata, subject, and attributes. Dynamic resolution MUST NOT accept raw SQL
and MUST occur once per protected query rather than once per returned row.

#### Scenario: Subject has an organization data scope
- **WHEN** the host resolves a dynamic boundary for a subject whose effective grant selects an
  organization scope
- **THEN** it returns one typed organization constraint that is applied to the business query

#### Scenario: Dynamic resolver returns no concrete constraint
- **WHEN** a protected dynamic query receives a null or unresolved boundary
- **THEN** execution fails closed before SQL reaches the database

#### Scenario: Dynamic integration is disabled
- **WHEN** the persistence authorization integration is disabled
- **THEN** no dynamic resolver is called and the original business query remains unchanged

