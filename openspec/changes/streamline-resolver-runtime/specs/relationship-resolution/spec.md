## MODIFIED Requirements

### Requirement: Forward, reverse, and batch resolution
Resolvers MUST independently declare supported forward relations, reverse relations, or attributes
through the corresponding operation interfaces, and every collection operation MUST be bounded. A
single named resolver MAY implement multiple operation interfaces without declaring unsupported
methods.

#### Scenario: Batch frontier is resolved
- **WHEN** the evaluator requests the same relation for multiple objects
- **THEN** the owning forward resolver receives a single bounded batch request and returns results
  keyed by request

#### Scenario: Unsupported reverse operation
- **WHEN** a policy used by `listObjects` requires reverse resolution not registered for its relation
- **THEN** policy validation rejects that listing operation before traversal starts

#### Scenario: Attribute requests span resolvers
- **WHEN** one logical evaluator batch requests attributes owned by different attribute resolvers
- **THEN** the registry groups bounded physical batches by owner and returns one merged logical result
