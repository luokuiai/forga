## Context

The evaluator already tracks an optional consistency token, but the forward relationship adapter
always sends an empty context and reduces each response to relationship entries. Dynamic grant
lookups return a token per request but cannot receive the token already established by an earlier
read. Bulk prefetch therefore cannot preserve one snapshot protocol across graph frontiers.

Forga must keep the token opaque. Hosts create snapshots and interpret tokens; the SDK only passes a
token to later reads and rejects unequal returned tokens.

## Goals / Non-Goals

**Goals:**

- Use one immutable read context for consistency and deadline propagation.
- Use one consistency-aware result for every bounded lookup batch.
- Preserve a token through direct checks, traversal, dynamic grants, bulk prefetch, and listing.
- Reject incomplete batches and consistency conflicts before caching or evaluating their values.
- Keep physical reads batched by resolver and bounded by existing limits.

**Non-Goals:**

- Creating host transactions, snapshots, tokens, caches, or storage schemas.
- Interpreting token contents or requiring one token format.
- Adding compatibility overloads for the replaced lookup APIs.
- Extending attribute or optional scope resolution in this change.

## Decisions

### Use an evaluator-owned read context

Add `EvaluationReadContext` in the neutral evaluation package with optional consistency and absolute
deadline fields. `RelationshipLookup` and `PermissionGrantLookup` accept this context directly.
Resolver adapters convert it to the existing resolver request context.

This avoids making the evaluator depend on resolver transport wrappers while replacing separate
deadline-only overloads with one protocol.

### Return one token per logical batch

Add immutable `BatchResolution<K, V>` containing a complete result map and an optional consistency
token. A physical resolver response may still carry its transport context per item, but the registry
adapter validates those contexts and exposes one aggregate token to the evaluator.

One token per batch makes the snapshot contract explicit and avoids storing duplicate tokens on
every grant or relationship value. `PermissionGrantResult` is removed; grant values become Boolean.

### Propagate tokens sequentially across physical resolver batches

The registry adapter processes resolver groups and bounded chunks with a local consistency
accumulator initialized from `EvaluationReadContext`. After each response, it validates and adopts
the returned token before creating the next physical request. This ensures later resolver calls
receive a token established earlier in the same logical lookup.

The adapter continues batching requests and never performs one lookup per result item.

### Accept results before caching values

`EvaluationState` exposes its current `EvaluationReadContext` and accepts a batch token before the
evaluator copies values into request-local caches. A conflicting token records
`CONSISTENCY_CONFLICT` and the batch contributes no authorization result.

Bulk prefetch records its established token alongside shared immutable lookup results. Each
per-decision evaluation state starts from that token while retaining independent limits, failure,
proof, and deadline state.

### Treat absent tokens as unversioned reads

An absent response token preserves an already established request token but does not establish a
new one. Unequal non-empty tokens always fail closed. Requiring versioned reads is outside this
change; hosts that need that guarantee must return tokens for their reads.

## Risks / Trade-offs

- [Breaking lookup API] → Update all core tests, Starter assembly tests, and the runnable example in
  the same change; no legacy bridge remains.
- [Sequential resolver groups reduce parallelism] → Current registry dispatch is already sequential;
  bounded batch size and grouping remain unchanged.
- [Unversioned host reads cannot be verified] → Keep absence explicit and never claim snapshot
  consistency when no token is returned.
- [Bulk checks share one physical snapshot token] → Seed each independent decision from the
  operation-local prefetch token while keeping all mutable evaluation counters independent.
