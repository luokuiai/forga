## 1. Isolate Bulk State

- [x] 1.1 Separate operation-local prefetch cache from per-decision evaluation state
- [x] 1.2 Account logical relation lookups per decision even for prefetched cache hits
- [x] 1.3 Keep speculative prefetch bounded and allow final evaluation to resolve missing entries

## 2. Regression Coverage

- [x] 2.1 Verify tight visited-node limits do not leak across bulk decisions
- [x] 2.2 Verify prefetch cannot bypass per-decision resolver-call limits
- [x] 2.3 Verify each bulk decision receives a fresh timeout window
- [x] 2.4 Verify resolver failures remain fail closed when prefetch cannot complete

## 3. Verification

- [x] 3.1 Run focused core tests and Checkstyle
- [x] 3.2 Validate the OpenSpec change strictly
- [x] 3.3 Run repository-wide clean check and inspect the final diff
