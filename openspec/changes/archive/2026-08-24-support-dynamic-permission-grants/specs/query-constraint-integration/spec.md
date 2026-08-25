## ADDED Requirements

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
