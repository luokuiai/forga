## Context

Bulk authorization uses eager graph-frontier prefetching to avoid N+1 relationship lookups. The
prefetched results currently live in the same `EvaluationState` used to evaluate every decision.
That state also owns mutable limits, deadline, proof, cycle detection, and failure state, causing
unrelated requests to consume one another's budgets.

## Goals / Non-Goals

**Goals:**

- Preserve set-based resolver calls at each graph frontier.
- Make every decision enforce the same logical limits as an individual `check`.
- Keep resolver results reusable across decisions without sharing mutable decision state.
- Bound speculative prefetch work and fall back to ordinary evaluation when prefetch stops.

**Non-Goals:**

- Changing public evaluator or resolver APIs.
- Parallelizing decision evaluation.
- Changing policy expression semantics or resolver ownership.

## Decisions

### Share only the forward relationship cache

Bulk prefetch writes immutable relationship entry lists into one operation-local cache. Each final
decision receives a new `EvaluationState` that references that cache but owns its own deadline,
proof, active path, counters, consistency, and failure reason. This retains batching without making
decision correctness depend on request order.

The alternative of resetting the existing state between decisions was rejected because it would
still share one deadline and would make it easy for future mutable fields to leak across decisions.

### Count logical lookups independently from physical resolver batches

Each decision tracks the unique relation lookup requests it uses. The first use consumes one
resolver-call unit even when prefetch already populated the shared cache; repeated use of the same
lookup within that decision remains memoized. This matches individual evaluation and prevents
prefetch from bypassing resolver-call limits.

The prefetch pass retains a separate physical-call budget. If it reaches a bound or resolver
failure, it stops optimizing; final decisions resolve any missing data under their own limits.

### Start the decision timeout when final evaluation begins

The speculative prefetch pass has its own bounded deadline. Each decision derives a fresh deadline
when its final evaluation state is created, so earlier requests and prefetch duration cannot consume
later decisions' timeout. Resolver calls made during final evaluation receive that deadline.

## Risks / Trade-offs

- [A failed prefetch can be followed by individual resolver calls] -> Treat prefetch as an optional
  optimization and preserve correctness over avoiding calls on exceptional paths.
- [The shared cache is mutable] -> Keep it operation-local and evaluate decisions sequentially; all
  stored values are immutable copies.
- [Logical resolver accounting differs from physical batch count] -> Define limits in terms of the
  work required by each decision, which preserves single/bulk equivalence.

