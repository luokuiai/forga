## ADDED Requirements

### Requirement: Consistent host reads within one decision
The evaluator MUST pass its current opaque consistency token and absolute deadline to every
relationship and dynamic grant batch, MUST establish its token from the first versioned response,
and MUST reject a later conflicting token before using that response.

#### Scenario: Relationship read establishes grant context
- **WHEN** a relationship response establishes a consistency token before evaluation reaches a
  dynamic grant
- **THEN** the grant batch receives that token in its read context

#### Scenario: Grant read establishes relationship context
- **WHEN** a dynamic grant response establishes a consistency token before evaluation reaches
  another relationship frontier
- **THEN** the relationship batch receives that token in its read context

#### Scenario: Host reads conflict
- **WHEN** two versioned host reads in one decision return unequal consistency tokens
- **THEN** evaluation fails closed with `CONSISTENCY_CONFLICT`

#### Scenario: Bulk prefetch establishes a snapshot
- **WHEN** a bounded bulk prefetch establishes a consistency token for its shared immutable results
- **THEN** each independent decision using those results starts with that token while retaining its
  own limits, deadline, proof, and failure state
