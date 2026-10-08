## 1. Web assembly

- [x] 1.1 Make Web enforcement unconditional on contributors or authorizer discovery; verify startup-failure and annotation-only tests.
- [x] 1.2 Move interceptor implementation to Starter-internal code and remove the public constructor API; verify MVC enforcement tests.
- [x] 1.3 Add immutable code-configured Web scope and startup validation; verify include/exclude, invalid, duplicate, and out-of-scope tests.

## 2. Total enable switch

- [x] 2.1 Gate Sa-Token and Spring Security provider auto-configuration on `@EnableForga`; verify enabled and disabled adapter tests.

## 3. Documentation and verification

- [x] 3.1 Update README for Starter-managed interceptor and path-scope configuration; verify no manual-construction example remains.
- [x] 3.2 Run OpenSpec validation and `./gradlew check` (tests and Checkstyle); address failures.
- [x] 3.3 Remove the unrequested Web-disable switch, migrate the example to endpoint authorization, and rerun `./gradlew check`.
- [x] 3.4 Verify equivalent URI-template names remain in scope and rerun `./gradlew clean check`.
