## MODIFIED Requirements

### Requirement: Host-owned effective grant resolution
Forga MUST expose a domain-neutral, batch-capable permission grant lookup that receives complete
authorization requests plus the evaluator's current consistency token and deadline. The lookup MUST
return one complete Boolean result map and one optional batch consistency token without owning host
roles, permission tables, snapshots, caches, or invalidation rules.

#### Scenario: Host resolves a cached grant
- **WHEN** a policy evaluates a grant leaf for a subject whose effective permissions are cached by
  the host
- **THEN** the lookup receives that subject and requested permission directly in a bounded batch
- **AND** the host does not enumerate every subject holding the permission

#### Scenario: Host permission changes
- **WHEN** the host invalidates or versions its effective permission snapshot after a grant changes
- **THEN** later checks observe the host lookup's updated result without recompiling the policy

#### Scenario: Earlier read establishes grant context
- **WHEN** an earlier host read establishes a consistency token
- **THEN** the dynamic grant batch receives the same token with its absolute deadline

### Requirement: Complete fail-closed grant batches
Grant lookup batches MUST return exactly one non-null Boolean result for every submitted request,
MUST reject missing or extra results and consistency conflicts, and MUST fail closed on lookup
exceptions, deadline exhaustion, or malformed responses.

#### Scenario: Bulk checks use dynamic grants
- **WHEN** multiple distinct checks reach grant leaves in one `bulkCheck`
- **THEN** the lookup receives bounded batches rather than one physical call per check
- **AND** each bulk decision remains equivalent to evaluating that check individually

#### Scenario: Grant batch is incomplete
- **WHEN** a grant lookup omits one submitted request or returns an unexpected request
- **THEN** affected authorization decisions are denied with a stable resolver failure reason

#### Scenario: Grant consistency conflicts
- **WHEN** a grant batch returns a token different from the token already established for the
  evaluation
- **THEN** evaluation fails closed with the consistency conflict reason
