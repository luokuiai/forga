## MODIFIED Requirements

### Requirement: Bounded fail-closed evaluation
The evaluator MUST enforce configured depth, visited-node, resolver-call, intermediate-result,
batch-size, and per-evaluation timeout limits, MUST derive a fresh absolute deadline for every
evaluation, and MUST detect active-path cycles.

#### Scenario: Cyclic relationship graph
- **WHEN** evaluation encounters a relation cycle without another valid proof
- **THEN** the request terminates without recursion overflow and returns a denied bounded result

#### Scenario: Evaluation limit exceeded
- **WHEN** any configured evaluation limit is exceeded
- **THEN** the engine stops evaluation and returns a structured fail-closed result

#### Scenario: Singleton evaluator handles later request
- **WHEN** two requests begin at different times with the same configured timeout duration
- **THEN** each request receives a deadline derived from its own evaluation start
