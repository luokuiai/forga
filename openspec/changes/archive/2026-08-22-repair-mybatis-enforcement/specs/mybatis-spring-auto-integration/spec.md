## MODIFIED Requirements

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
