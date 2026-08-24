## MODIFIED Requirements

### Requirement: Consistent single and bulk checks
`check` and `bulkCheck` MUST produce equivalent decisions for identical inputs. `bulkCheck` MUST
group distinct relationship requests at each graph frontier into bounded resolver batches,
including frontiers discovered through subject sets and traversal, instead of issuing one resolver
query per object. Each bulk decision MUST own independent visited-node, logical resolver-call,
intermediate-result, cycle, proof, failure, and timeout state; bulk requests MAY share only
operation-local immutable resolver results used to avoid duplicate physical lookups.

#### Scenario: Batch matches individual checks
- **WHEN** the same checks are evaluated individually and in one batch under the same context
- **THEN** every decision and failure reason is equivalent

#### Scenario: Distinct objects share a frontier
- **WHEN** a bulk check evaluates one relation on multiple distinct objects
- **THEN** the resolver receives those relationship requests in one bounded batch

#### Scenario: Traversal discovers another frontier
- **WHEN** a batched traversal resolves multiple intermediate subject-set objects
- **THEN** the nested relation requests for those objects are sent as the next bounded batch

#### Scenario: Tight limits are isolated per decision
- **WHEN** multiple checks each fit independently within configured limits
- **THEN** evaluating them in one batch does not consume another decision's limits or deadline

#### Scenario: Prefetch does not bypass logical resolver limits
- **WHEN** one check requires more distinct relationship lookups than its resolver-call limit
- **THEN** the bulk decision fails with the same limit reason as an individual check even if all
  relationship results were physically prefetched

