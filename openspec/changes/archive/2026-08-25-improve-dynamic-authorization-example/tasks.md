## 1. Resolver Ergonomics

- [x] 1.1 Add complete fail-closed default reverse and attribute responses to
  `RelationshipResolver`.
- [x] 1.2 Add core tests for forward-only implementations, batch completeness, ordering, and
  consistency preservation.

## 2. Dynamic Authorization Example

- [x] 2.1 Add a thread-safe persistence-shaped host authorization data store for role assignments,
  permission grants, and data-scope grants.
- [x] 2.2 Adapt `PermissionGrantLookup` to resolve batches from one immutable host data snapshot.
- [x] 2.3 Add a dynamic MyBatis boundary resolver that maps effective scopes to allowlisted typed
  query constraints and fails closed for missing grants.
- [x] 2.4 Update the relationship example to rely on undeclared operation defaults.
- [x] 2.5 Add integration tests proving permission and data-scope changes are observed without
  replacing the compiled policy.
- [x] 2.6 Align the example data-scope vocabulary and typed constraints with `OWNER`, `DEPARTMENT`,
  and `TENANT`.
- [x] 2.7 Scope example role assignments, permission grants, and data scopes by effective tenant
  and exact `USER` or `MEMBERSHIP` subject.
- [x] 2.8 Add a dynamic concurrent-appointment relationship and validate the request-selected
  department before resolving `DEPARTMENT` query scope.
- [x] 2.9 Add integration tests for target-tenant membership grants, source-user isolation,
  department switching, and inactive appointments.

## 3. Documentation And Verification

- [x] 3.1 Document stable policy expressions versus dynamic host authorization data and link the
  expanded example.
- [x] 3.2 Run targeted core and example tests plus Checkstyle.
- [x] 3.3 Run `./gradlew clean check` and strict OpenSpec validation.
- [x] 3.4 Re-run example tests, full checks, and strict validation after scope alignment.
- [x] 3.5 Document the concurrent-appointment example and run targeted plus full verification.
