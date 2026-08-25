## Why

The README shows isolated core API snippets but does not provide a runnable Spring Boot application
that demonstrates the required host-owned `CompiledPolicy`, resolver, and subject provider beans.
Users therefore encounter generic missing-bean failures without a complete integration reference.

## What Changes

- Add a non-published Spring Boot example module built and tested with the repository.
- Demonstrate `@EnableForga`, policy compilation, an in-memory relationship resolver, and an
  HTTP-header authenticated subject provider.
- Expose one protected document endpoint with allowed, unauthenticated, and denied behavior.
- Link the runnable example from the README and document when `CompiledPolicy` is required.

## Capabilities

### New Capabilities

- `spring-boot-example`: Runnable reference integration for the required host-owned Spring beans and
  a protected HTTP request flow.

### Modified Capabilities

None.

## Impact

The change adds an example-only Gradle module and README documentation. It does not change SDK
runtime behavior, public APIs, or host data ownership.
