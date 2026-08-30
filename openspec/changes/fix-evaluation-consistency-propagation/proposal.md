## Why

Forward relationship lookup currently discards resolver consistency tokens and starts every read
with an empty consistency context. A decision can therefore combine relationship and dynamic grant
results from different host snapshots even though the evaluator exposes consistency-conflict
semantics.

## What Changes

- **BREAKING** Replace deadline-only evaluator lookup methods with one domain-neutral read context
  carrying the current opaque consistency token and deadline.
- **BREAKING** Return one consistency-aware batch result from relationship and dynamic permission
  grant lookups instead of attaching or discarding consistency inconsistently.
- Establish a decision consistency token from the first versioned host read, propagate it to later
  reads, and fail closed when a response conflicts.
- Preserve bounded batching, request-local memoization, resolver ownership, and host-defined token
  semantics.
- Keep business data, snapshot creation, storage transactions, and token interpretation owned by
  the host.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `authorization-evaluation`: Require one decision to propagate and validate consistency across
  relationship and dynamic grant reads.
- `relationship-resolution`: Require forward resolver requests and responses to preserve the
  evaluator's consistency context through the registry adapter.
- `dynamic-permission-grants`: Require grant batches to receive the established consistency context
  and return one batch consistency result.

## Impact

Affected public and internal APIs are in `forga-core`: evaluation read context and batch result value
objects, `RelationshipLookup`, `PermissionGrantLookup`, resolver registry adapters, and evaluator
state. Host implementations of the changed lookup contracts must adopt the new signatures. No
storage schema, optional integration dependency, or business model is introduced.
