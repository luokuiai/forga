# dynamic-permission-grants Specification

## Purpose
TBD - created by archiving change support-dynamic-permission-grants. Update Purpose after archive.
## Requirements
### Requirement: Host-owned effective grant resolution
Forga MUST expose a domain-neutral, batch-capable permission grant lookup that receives complete
subject, object, permission, attribute, deadline, and consistency context without owning host roles,
permission tables, snapshots, caches, or invalidation rules.

#### Scenario: Host resolves a cached role grant
- **WHEN** a policy evaluates a grant leaf for a subject whose effective permissions are cached by
  the host
- **THEN** the lookup receives that subject and requested permission directly
- **AND** the host does not enumerate every subject holding the permission

#### Scenario: Host permission changes
- **WHEN** the host invalidates or versions its effective permission snapshot after a role grant
  changes
- **THEN** later checks observe the host lookup's updated result without recompiling the policy

### Requirement: Complete fail-closed grant batches
Grant lookup batches MUST return exactly one non-null result for every submitted request, MUST reject
missing or extra results and consistency conflicts, and MUST fail closed on lookup exceptions,
deadline exhaustion, or malformed responses.

#### Scenario: Bulk checks use dynamic grants
- **WHEN** multiple distinct checks reach grant leaves in one `bulkCheck`
- **THEN** the lookup receives bounded batches rather than one physical call per check
- **AND** each bulk decision remains equivalent to evaluating that check individually

#### Scenario: Grant batch is incomplete
- **WHEN** a grant lookup omits one submitted request or returns an unexpected request
- **THEN** affected authorization decisions are denied with a stable resolver failure reason

#### Scenario: Grant consistency conflicts
- **WHEN** grant and relationship results in one evaluation report different consistency tokens
- **THEN** evaluation fails closed with the consistency conflict reason

### Requirement: Grant expressions remain composable and check-only
The immutable policy model MUST compose a dynamic grant leaf with union, intersection, exclusion,
relations, traversal, and caveats. Object listing through a grant leaf MUST fail closed without
attempting an unbounded subject-to-object scan.

#### Scenario: Dynamic role grant or object ownership allows access
- **WHEN** a union contains a grant leaf and an ownership relation
- **THEN** either successful branch can allow the check without proof steps from failed branches

#### Scenario: Grant-only permission is listed
- **WHEN** `listObjects` evaluates a permission whose result depends on a grant leaf
- **THEN** the grant branch contributes no objects and performs no unbounded scan
