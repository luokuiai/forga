## 1. Runtime Capability Source

- [x] 1.1 Replace capability-dependent policy compilation with structural compilation and update policy tests.
- [x] 1.2 Add runtime requirement discovery and validate relations, caveats, attributes, and grants from actual registrations.

## 2. Specialized Resolver Contracts

- [x] 2.1 Replace the bundled resolver SPI and descriptor with named forward, reverse, and attribute resolver interfaces.
- [x] 2.2 Update the registry and forward/reverse adapters for specialized capability ownership and malformed response handling.
- [x] 2.3 Migrate resolver fixtures and contract tests to cover single- and multi-capability implementations.

## 3. Attribute Evaluation

- [x] 3.1 Add caveat requirement declarations, caveat evaluation context, and evaluator attribute lookup contracts.
- [x] 3.2 Add the registry attribute lookup adapter with batching, aggregation, deadline, consistency, and fail-closed validation.
- [x] 3.3 Integrate attribute resolution into single check and traversal caveat evaluation.
- [x] 3.4 Batch caveat attribute reads in bulk checks while preserving per-decision limits and equivalent outcomes.
- [x] 3.5 Resolve and evaluate object attributes for bounded `listObjects` candidates and cursor consistency.

## 4. Integration And Verification

- [x] 4.1 Update Spring Boot assembly, runtime validation, and override behavior for specialized resolvers and attribute lookup.
- [x] 4.2 Migrate the runnable example and README integration guidance without adding host-domain concepts to core.
- [x] 4.3 Run strict OpenSpec validation and focused core and Starter tests with Checkstyle.
- [x] 4.4 Run `./gradlew clean check` and record all tasks complete only after the full build passes.
