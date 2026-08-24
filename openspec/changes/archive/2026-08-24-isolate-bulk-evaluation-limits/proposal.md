## Why

`bulkCheck` currently shares one mutable evaluation state across every request, so visited-node,
resolver-call, and timeout limits consumed by earlier requests can incorrectly deny later requests.
This violates the existing contract that bulk and individual checks produce equivalent decisions.

## What Changes

- Give each bulk request independent evaluation limits, deadline, proof, cycle path, and failure state.
- Retain frontier batching and shared relationship result memoization across the bulk operation.
- Account for logical relationship work per decision independently from physical resolver batching.
- Add regression coverage for tight node, resolver-call, timeout, and resolver-failure limits.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `authorization-evaluation`: Clarify that bulk requests isolate per-decision evaluation limits while
  sharing only immutable resolver results and consistency context required for batching.

## Impact

The change is limited to `forga-core` evaluator internals, evaluator tests, and the authorization
evaluation specification. Public APIs and host-owned relationship storage contracts are unchanged.

