## Why

Business applications commonly keep permission names fixed while dynamically assigning permissions
to roles and materializing effective permission snapshots per subject. Forga currently requires
object-to-subject relationship enumeration and fixed MyBatis constraints, which cannot consume that
model efficiently or safely at scale.

## What Changes

- Add a composable `grant()` policy leaf backed by a host-owned, batch-capable
  `PermissionGrantLookup` receiving the complete check request.
- Make grant lookup results fail closed, deadline-aware, consistency-aware, and equivalent between
  single and bulk checks.
- Add explicit dynamic MyBatis boundaries resolved at request time from the authenticated subject
  and authorization attributes.
- Auto-wire optional host grant and MyBatis boundary resolvers while preserving existing fixed
  policies and statement declarations.
- Document how subject-centric permission snapshots and dynamic data scopes integrate without
  moving host role tables, caches, or business metadata into Forga.
- Runtime policy replacement, role persistence, permission administration, and host cache ownership
  remain out of scope.

## Capabilities

### New Capabilities

- `dynamic-permission-grants`: host-owned subject-centric effective grants used as composable policy
  leaves.

### Modified Capabilities

- `authorization-evaluation`: grant leaves participate in bounded single and bulk evaluation.
- `query-constraint-integration`: typed constraints may be selected at request time.
- `mybatis-spring-auto-integration`: statements may declare dynamic boundaries resolved by a host
  bean.
- `runtime-integration`: Spring assembles the optional grant lookup into the evaluator.

## Impact

- New public APIs in `forga-core` and `forga-mybatis` with compatibility-preserving constructor
  overloads.
- Evaluator batching, policy canonicalization, MyBatis interception, Starter assembly, tests, and
  documentation are affected.
- Host applications retain ownership of permission snapshots, cache invalidation, role grants,
  data-scope semantics, and persistence.
