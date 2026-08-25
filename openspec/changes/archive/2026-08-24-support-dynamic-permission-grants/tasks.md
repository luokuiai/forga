## 1. Dynamic Permission Grants

- [x] 1.1 Add the grant policy expression and immutable lookup request/result contracts.
- [x] 1.2 Integrate grant resolution into bounded single and bulk evaluation.
- [x] 1.3 Test allowed, denied, malformed, failed, consistency, composition, and batching behavior.

## 2. Dynamic Query Constraints

- [x] 2.1 Add explicit dynamic MyBatis boundaries and the request-time boundary resolver.
- [x] 2.2 Resolve concrete boundaries before SQL translation and fail closed on invalid results.
- [x] 2.3 Test fixed compatibility, dynamic scope selection, disabled behavior, and invalid results.

## 3. Spring And Documentation

- [x] 3.1 Auto-wire grant lookups and boundary resolvers with host override behavior.
- [x] 3.2 Add Starter tests for configured and missing dynamic dependencies.
- [x] 3.3 Document the subject-centric snapshot and dynamic data-scope integration pattern.

## 4. Verification

- [x] 4.1 Run focused module tests and Checkstyle.
- [x] 4.2 Run the complete Gradle check and strict OpenSpec validation.
