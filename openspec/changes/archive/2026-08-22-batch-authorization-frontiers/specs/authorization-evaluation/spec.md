## MODIFIED Requirements

### Requirement: Consistent single and bulk checks
`check` and `bulkCheck` MUST produce equivalent decisions for identical inputs. `bulkCheck` MUST
group distinct relationship requests at each graph frontier into bounded resolver batches,
including frontiers discovered through subject sets and traversal, instead of issuing one resolver
query per object.

#### Scenario: Batch matches individual checks
- **WHEN** the same checks are evaluated individually and in one batch under the same context
- **THEN** every decision and failure reason is equivalent

#### Scenario: Distinct objects share a frontier
- **WHEN** a bulk check evaluates one relation on multiple distinct objects
- **THEN** the resolver receives those relationship requests in one bounded batch

#### Scenario: Traversal discovers another frontier
- **WHEN** a batched traversal resolves multiple intermediate subject-set objects
- **THEN** the nested relation requests for those objects are sent as the next bounded batch
