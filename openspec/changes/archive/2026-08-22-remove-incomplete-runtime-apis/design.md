## Context

Pre-release compatibility APIs remain from earlier experiments. They are not used by production
assembly, and some weaken guarantees described by their names. Proof steps are appended to one
mutable list even when a boolean branch later fails.

## Goals / Non-Goals

**Goals:**

- Keep only public APIs backed by a production execution path.
- Make every scoped object check enforce ownership resolution.
- Make MyBatis statement metadata match exactly what the interceptor consumes.
- Produce proof steps only from a successful authorization path.
- Preserve focused startup errors for authentication provider discovery.

**Non-Goals:**

- Removing attribute resolver contracts, which hosts can still use independently.
- Renaming the MyBatis statement type or changing its boundary model.
- Changing permission decision semantics.

## Decisions

1. Delete `ForgaRequestContext`, `ForgaRequestScope`, `ForgaRuntimeComponents`, and the public
   assembler factory. A package-private validator remains beside actual Spring assembly.
2. `ScopedAuthorizationService` requires `ObjectScopeResolver` in every constructor. Cross-scope
   access defaults to deny unless explicitly supplied.
3. `MyBatisStatementAuthorization` contains only `statementId` and `boundary`; resource and
   permission were never read and therefore did not authorize anything.
4. Evaluation records a proof checkpoint before each conditional branch and truncates the list when
   that path fails. Exclusion preserves base proof only when the excluded path does not match.
5. MyBatis auto-configuration selects from `ObjectProvider<AuthenticatedSubjectProvider>` and
   raises the same precise Forga errors for zero or multiple providers before interceptor creation.

## Risks / Trade-offs

- [Pre-release callers stop compiling] -> Migration is mechanical and exposes previously missing
  strict dependencies.
- [Proof lists become shorter] -> Removed steps were from failed paths and were misleading audit
  evidence.

## Migration Plan

Use a strict scoped constructor with an object-scope resolver. Remove resource and permission
arguments from statement declarations. Inject assembled Spring beans directly instead of wrapper
components. Outstanding behavior remains source-compatible elsewhere.

## Open Questions

None.
