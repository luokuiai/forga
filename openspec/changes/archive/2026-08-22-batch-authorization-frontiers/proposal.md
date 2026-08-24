## Why

`bulkCheck` currently loops over single checks and only benefits from accidental cache reuse. Checks
for distinct objects therefore issue one resolver call per object instead of using the batch
resolver contract.

## What Changes

- Plan relation requests for all bulk items at the same graph depth.
- Resolve each unique frontier in one bounded lookup batch and populate the shared evaluation cache.
- Discover subject-set and traversal frontiers from the prior frontier responses.
- Preserve request order, decision semantics, proof generation, limits, and fail-closed behavior.
- Add direct, subject-set, and traversal batching tests across distinct objects.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `authorization-evaluation`: Make the existing bulk frontier batching requirement explicit for
  direct and recursively discovered relationship frontiers.

## Impact

The change is internal to `AuthorizationEvaluator` and its tests. It does not change public models,
resolver interfaces, policies, or host data ownership.
