## MODIFIED Requirements

### Requirement: Composable permission evaluation
The engine MUST evaluate direct relations, subject sets, union, intersection, exclusion, relation
traversal, and caveats from one immutable policy model. An allowed decision proof MUST contain only
steps belonging to a successful expression path.

#### Scenario: Permission traverses a parent relation
- **WHEN** a permission grants access through a parent object relation and the subject is related to
  that parent
- **THEN** `check` returns an allowed decision with only the successful traversed proof

#### Scenario: Failed union branch precedes an allowed branch
- **WHEN** an earlier union branch records relationship steps but fails and a later branch succeeds
- **THEN** the allowed proof excludes every step from the failed branch

#### Scenario: Excluded subject is denied
- **WHEN** a subject satisfies the base expression and also satisfies its exclusion expression
- **THEN** `check` returns a denied decision
