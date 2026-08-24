## 1. Cursor Protection

- [x] 1.1 Add evaluator-instance AES-GCM cursor encoding and authenticated decoding
- [x] 1.2 Reject malformed, modified, legacy, and request-mismatched cursors

## 2. Pagination Continuity

- [x] 2.1 Separate resolver input and output continuation state
- [x] 2.2 Retain the current continuation while local results remain and reset offset on advance
- [x] 2.3 Add multi-page tests proving no skipped objects across resolver continuations

## 3. Verification

- [x] 3.1 Run focused core tests and Checkstyle
- [x] 3.2 Validate the OpenSpec change strictly
- [x] 3.3 Run the repository-wide clean check
