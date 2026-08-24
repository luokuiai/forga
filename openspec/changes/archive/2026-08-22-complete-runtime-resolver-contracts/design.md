## Context

Resolver selection scans an unordered map and chooses the first matching declaration. Runtime
startup also treats reverse lookup as mandatory for every check policy. `EvaluationLimits` stores
an absolute `Instant`, so a singleton evaluator eventually expires permanently, while adapter
requests currently omit deadlines.

## Goals / Non-Goals

**Goals:**

- Make capability routing unique and deterministic.
- Keep check-only runtime assembly independent from reverse lookup.
- Derive one deadline for each check, bulk check, or listing evaluation and propagate it to every
  resolver call.
- Preserve existing host lookup lambdas and batch grouping.

**Non-Goals:**

- Adding a Spring properties object for limits.
- Automatically resolving attributes for caveats.
- Implementing graph-frontier bulk evaluation in this change.

## Decisions

1. `ResolverRegistry` builds immutable maps keyed by forward relation, reverse relation, and
   attribute. A duplicate capability fails construction and identifies both resolver names.
2. Runtime startup validates only forward capabilities required by `check`. `listObjects` remains
   fail closed through the reverse adapter when its optional capability is unavailable.
3. `EvaluationLimits` stores `Optional<Duration> timeout`. Each new evaluation state converts it to
   one absolute deadline, preventing timeout extension during recursion and avoiding singleton
   expiration.
4. Lookup interfaces gain context-aware default overloads. Existing functional implementations
   remain source compatible; registry adapters override the overload and translate the deadline to
   `ResolverDeadline`.

## Risks / Trade-offs

- [Full record constructor is source incompatible] -> The project is pre-release; document the
  direct `Instant` to `Duration` migration and retain simpler constructors.
- [A legacy lookup ignores context] -> Default methods preserve behavior; registry adapters and
  context-aware hosts receive deadlines explicitly.
- [Missing reverse capability is found at first listing call] -> The operation fails closed without
  preventing check-only applications from starting.

## Migration Plan

Replace absolute deadlines passed to `EvaluationLimits` with timeout durations. Remove duplicate
capability declarations or split their relation ownership. No persistence migration is required.

## Open Questions

None.
