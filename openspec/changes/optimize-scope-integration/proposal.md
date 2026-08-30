## Why

`forga-scope` is the optional, opinionated integration layer for tenant, organization, position, and similar host authorization boundaries, but its current single-item ownership and cross-scope Boolean resolvers cannot share evaluator consistency state or support set-oriented bulk checks. Scope switching also has two competing sources for the entry permission, which can make direct switch checks and scoped object checks disagree.

## What Changes

- **BREAKING** Replace single-item object-scope and cross-scope resolver methods with complete batch lookups.
- Add bulk scoped authorization so scope entry, boundary grants, and ordinary object permissions can be evaluated without N+1 host calls.
- **BREAKING** Make the configured scope-entry permission the single source of truth; remove it from each scope-switch request.
- Return phase-aware scoped decisions that distinguish missing scope, entry denial, unresolved ownership, cross-scope denial, and ordinary object denial without inventing tenant or organization concepts in Core.
- Preserve `ScopeRef`, active/acting context, policy templates, and query helpers as business-friendly integration APIs. Tenant, organization, position, and concurrent assignment semantics remain owned by `forga-scope` and the host.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `scope-authorization`: Add batch and consistency-aware scope authorization, one configured entry permission, and phase-aware decisions.

## Impact

- Affects `forga-scope` public resolver, request, service, and decision APIs.
- Host applications implement set-oriented ownership and cross-scope grant lookups over their own storage.
- Host applications keep composed scope reads in their request transaction or equivalent snapshot boundary.
- Existing scope call sites and documentation must migrate directly; no compatibility facade is retained during development.

## Non-Goals

- Core will not define tenant, organization, department, position, appointment, or concurrent-role semantics.
- The change will not prescribe host tables, hierarchy storage, or the meaning of a cross-scope grant.
- The change will not replace set-based query constraints with per-row authorization.
