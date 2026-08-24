## Context

The resolver interface is batch-capable, but `bulkCheck` evaluates requests sequentially. A shared
cache only helps duplicate object/relation keys and does not batch distinct graph nodes.

## Goals / Non-Goals

**Goals:**

- Resolve unique relationship requests once per graph frontier across the bulk input.
- Discover recursive subject-set and traversal work from prior resolver responses.
- Reuse the existing evaluator for final ordered decisions and proofs.
- Keep planning bounded by existing batch, resolver-call, intermediate-result, and deadline limits.

**Non-Goals:**

- Changing single-check evaluation order or public APIs.
- Combining reverse object listing with forward bulk checks.
- Persisting evaluation plans between calls.

## Decisions

1. A request-scoped planner expands policy-only nodes immediately and groups relation-dependent
   nodes into a `RelationLookupRequest` frontier.
2. One lookup call resolves all unique missing requests in a frontier. Results populate the same
   cache used by normal evaluation.
3. Relation responses create the next frontier for subject-set recursion. Traversal responses pair
   each returned subject-set object with the traversal's nested expression.
4. Caveat branches are planned only when their request caveat evaluates true. Final decisions still
   run through the existing evaluator, preserving proof and boolean semantics.
5. A visited work set prevents planning cycles; runtime active-path checks remain authoritative for
   the final decision.

## Risks / Trade-offs

- [Boolean branches can be prefetched before short-circuiting] -> Work remains bounded and only
  populates cache; final evaluation retains its existing short-circuit semantics.
- [A batch resolver failure affects a frontier] -> The shared state records the structured failure
  and final requests fail closed consistently.

## Migration Plan

No migration is required. Resolver implementations receive larger batches for bulk checks and must
continue satisfying the existing batch response contract.

## Open Questions

None.
