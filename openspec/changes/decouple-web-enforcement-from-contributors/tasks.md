## 1. Starter Assembly

- [x] 1.1 Decouple common Web permission assembly from contributor presence and verify the Starter
  compiles with an empty contributor collection.
- [x] 1.2 Scope incomplete-registry validation to contributor-based integrations, require an
  authorizer instead of accepting a host interceptor, and verify a host without Web permission
  beans can still start.

## 2. Behavioral Tests

- [x] 2.1 Add annotation-only automatic enforcement coverage and verify the Starter creates and
  registers its interceptor when only an authorizer is supplied.
- [x] 2.2 Preserve contributor, disabled, and fail-closed coverage and verify the targeted Starter
  test suite passes.

## 3. Documentation and Validation

- [x] 3.1 Update Spring Web documentation to describe contributors as optional and verify examples
  do not require an empty contributor.
- [x] 3.2 Run Google Java formatting, Starter Checkstyle/tests, strict OpenSpec validation, and the
  full Gradle check successfully.
