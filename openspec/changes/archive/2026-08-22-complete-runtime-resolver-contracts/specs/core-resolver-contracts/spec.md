## ADDED Requirements

### Requirement: Unique resolver capability ownership
The resolver registry MUST assign each forward relation, reverse relation, and attribute capability
to exactly one resolver and MUST reject ambiguous declarations during construction.

#### Scenario: Two resolvers declare one forward relation
- **WHEN** differently named resolvers both declare forward support for the same relation
- **THEN** registry construction fails with both resolver names and the conflicting capability

### Requirement: Resolver deadline propagation
Context-aware evaluator lookup adapters MUST propagate the evaluation deadline to every forward and
reverse resolver request in the batch.

#### Scenario: Evaluation has a timeout
- **WHEN** an evaluator performs relationship resolution with a configured timeout
- **THEN** every resolver request receives the same absolute deadline for that evaluation
