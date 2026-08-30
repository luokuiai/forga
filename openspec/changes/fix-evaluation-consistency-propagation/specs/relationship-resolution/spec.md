## MODIFIED Requirements

### Requirement: Resolver consistency context
Resolver calls within one evaluation MUST propagate a common opaque consistency context. The
forward relationship registry adapter MUST validate all physical response contexts, MUST return one
consistency-aware logical batch, and MUST reject conflicting tokens before exposing relationship
entries to the evaluator.

#### Scenario: Resolver establishes a token
- **WHEN** the first forward resolver read establishes a consistency token
- **THEN** every subsequent physical and logical read in that evaluation receives the same token

#### Scenario: Responses within a batch conflict
- **WHEN** physical forward responses in one logical batch return unequal consistency tokens
- **THEN** relationship resolution fails closed with `CONSISTENCY_CONFLICT`

#### Scenario: Resolver returns no token
- **WHEN** a resolver returns an unversioned response while the request already carries a token
- **THEN** the established request token remains active for subsequent reads
