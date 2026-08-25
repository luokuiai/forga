## ADDED Requirements

### Requirement: Undeclared resolver operations default fail closed
`RelationshipResolver` SHALL provide complete empty default responses for reverse and attribute
operations so hosts implementing only declared forward capabilities do not need unrelated method
boilerplate. Default responses MUST preserve each submitted request and its consistency context.

#### Scenario: Forward-only resolver omits optional methods
- **WHEN** a host declares only forward relation capabilities and implements descriptor and forward
  resolution
- **THEN** the resolver compiles without host implementations for reverse or attribute resolution
- **AND** direct default calls return one empty response for every submitted request

#### Scenario: Undeclared default operation receives a batch
- **WHEN** a reverse or attribute batch is passed to the corresponding default method
- **THEN** the result contains exactly one response per request in request order
- **AND** no objects or attributes are granted
- **AND** request consistency is preserved
