## Why

Runtime assembly currently accepts ambiguous resolver capabilities, requires reverse resolution for
check-only hosts, and models an absolute deadline inside a singleton limits bean. Resolver calls
therefore have nondeterministic routing and do not receive a usable per-evaluation deadline.

## What Changes

- Reject duplicate forward, reverse, or attribute capability ownership at registry construction.
- Validate forward resolution for evaluator startup without requiring reverse capabilities until
  object listing is actually used.
- **BREAKING** Replace the absolute `EvaluationLimits.deadline` instant with a per-evaluation
  timeout duration.
- Propagate the derived request deadline through evaluator lookup contracts into forward and
  reverse `ResolverContext` values.
- Preserve compatibility for host lookup lambdas through default context-aware methods.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `core-resolver-contracts`: Require unique deterministic capability ownership and deadline
  propagation.
- `authorization-evaluation`: Establish deadline budgets per evaluation instead of at singleton
  construction.
- `runtime-integration`: Allow check-only assembly without reverse resolver capabilities.

## Impact

The change affects `EvaluationLimits`, evaluator lookup contracts, resolver adapters and Spring
startup validation. Hosts constructing the full `EvaluationLimits` record must replace
`Optional<Instant>` with `Optional<Duration>`. Resolver request and response contracts otherwise
remain unchanged.
