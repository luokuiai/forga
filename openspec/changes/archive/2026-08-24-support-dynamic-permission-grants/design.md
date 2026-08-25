## Context

Forga evaluates object-centric relations by loading relationship entries and comparing the requested
subject. Subject-centric RBAC systems instead materialize effective grants from user roles and role
permissions, often behind a versioned cache. Enumerating every subject holding a permission is both
the wrong query direction and incompatible with evaluator result bounds. MyBatis authorization has
a related limitation: its typed boundary is fixed before the current subject's effective data scope
is known.

## Goals / Non-Goals

**Goals:**

- Consume host-owned effective permission snapshots with one bounded batch lookup.
- Compose dynamic grants with existing relation, traversal, caveat, and boolean expressions.
- Preserve single/bulk equivalence, deadlines, consistency, and fail-closed behavior.
- Resolve a declared dynamic MyBatis boundary once per query from the current subject and request
  attributes, then apply the existing typed SQL translator.

**Non-Goals:**

- Owning role, grant, permission, cache, tenant, organization, menu, button, or data-scope tables.
- Interpreting host permission codes or scope values.
- Supporting reverse object listing from a subject-centric grant lookup.
- Replacing immutable compiled policy definitions at runtime.

## Decisions

Add a zero-argument `PermissionExpression.grant()` leaf. It delegates the complete `CheckRequest` to
a `PermissionGrantLookup`, so the host can query or cache effective permissions directly by subject.
The lookup is batch-oriented and returns one result per request with an optional consistency token.
It is a single neutral boundary rather than a role-specific API; hosts may dispatch internally when
multiple authorization sources exist.

The evaluator gets compatibility-preserving constructor overloads. Existing constructors use a
deny-all grant lookup, so existing policies behave identically. Spring injects a host lookup when
present and rejects a compiled policy containing `grant()` when none is registered. Bulk evaluation
prefetches distinct grant requests in bounded batches and shares only immutable operation-local
results. Missing, extra, null, conflicting, or exceptional results fail closed.

Dynamic grants are check-only. `listObjects` cannot derive objects from a subject-centric boolean
result and therefore returns a fail-closed empty result for a grant leaf. A future reverse grant
contract can add listing without forcing unbounded scans into this change.

Add `MyBatisAuthorizationBoundary.dynamic(id)` plus a
`MyBatisAuthorizationBoundaryResolver`. The resolver receives the declaration, subject, and neutral
request attributes and must return one concrete typed boundary. Fixed declarations pass through the
default resolver unchanged. Null or still-dynamic results fail before SQL execution. This keeps SQL
translation syntax-aware and parameterized while allowing a host snapshot to select different
constraint trees per request.

Alternatives rejected:

- Encoding effective grants as relationship enumeration: unbounded in the number of users.
- Putting role and data-scope concepts in core: violates host ownership and domain neutrality.
- Calling a per-row permission service from MyBatis: creates N+1 authorization queries.
- Passing raw SQL from the dynamic resolver: bypasses field allowlists and parameter binding.

## Risks / Trade-offs

- [Risk] A host lookup reports stale grants. -> Carry host consistency versions as opaque tokens and
  keep invalidation ownership explicit in the host adapter.
- [Risk] A dynamic boundary resolver returns an unsafe or incomplete result. -> Require a concrete
  typed boundary and run it through the existing translator validation.
- [Trade-off] Grant-only policies cannot list objects. -> Fail closed and document the check-only
  contract instead of pretending a boolean snapshot is reversible.

## Migration Plan

Existing policies, evaluator constructors, fixed MyBatis boundaries, and disabled integration remain
unchanged. Hosts opt in by using `grant()`, registering a `PermissionGrantLookup`, or declaring a
dynamic boundary with a resolver. Removing those declarations restores the prior paths.

## Open Questions

None.
