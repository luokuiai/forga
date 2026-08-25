## MODIFIED Requirements

### Requirement: Composable permission evaluation
The engine MUST evaluate direct relations, subject sets, dynamic grants, union, intersection,
exclusion, relation traversal, and caveats from one immutable policy model. An allowed decision proof
MUST contain only steps belonging to a successful expression path.

#### Scenario: Permission traverses a parent relation
- **WHEN** a permission grants access through a parent object relation and the subject is related to
  that parent
- **THEN** `check` returns an allowed decision with only the successful traversed proof

#### Scenario: Dynamic grant allows access
- **WHEN** a host grant lookup reports that the requested subject holds the requested permission
- **THEN** a grant expression returns an allowed decision without enumerating other subjects

#### Scenario: Failed union branch precedes an allowed branch
- **WHEN** an earlier union branch records relationship steps but fails and a later branch succeeds
- **THEN** the allowed proof excludes every step from the failed branch

#### Scenario: Excluded subject is denied
- **WHEN** a subject satisfies the base expression and also satisfies its exclusion expression
- **THEN** `check` returns a denied decision

### Requirement: Consistent single and bulk checks
`check` and `bulkCheck` MUST produce equivalent decisions for identical inputs. `bulkCheck` MUST
group distinct relationship and grant requests at each graph frontier into bounded resolver batches,
including frontiers discovered through subject sets and traversal, instead of issuing one resolver
query per object or grant. Each bulk decision MUST own independent visited-node, logical
resolver-call, intermediate-result, cycle, proof, failure, and timeout state; bulk requests MAY share
only operation-local immutable resolver results used to avoid duplicate physical lookups.

#### Scenario: Batch matches individual checks
- **WHEN** the same checks are evaluated individually and in one batch under the same context
- **THEN** every decision and failure reason is equivalent

#### Scenario: Distinct objects share a frontier
- **WHEN** a bulk check evaluates one relation on multiple distinct objects
- **THEN** the resolver receives those relationship requests in one bounded batch

#### Scenario: Distinct grants share a batch
- **WHEN** a bulk check evaluates dynamic grants for multiple distinct requests
- **THEN** the grant lookup receives those requests in bounded batches

#### Scenario: Traversal discovers another frontier
- **WHEN** a batched traversal resolves multiple intermediate subject-set objects
- **THEN** the nested relation requests for those objects are sent as the next bounded batch

#### Scenario: Tight limits are isolated per decision
- **WHEN** multiple checks each fit independently within configured limits
- **THEN** evaluating them in one batch does not consume another decision's limits or deadline

#### Scenario: Prefetch does not bypass logical resolver limits
- **WHEN** one check requires more distinct relationship or grant lookups than its resolver-call
  limit
- **THEN** the bulk decision fails with the same limit reason as an individual check even if all
  results were physically prefetched
