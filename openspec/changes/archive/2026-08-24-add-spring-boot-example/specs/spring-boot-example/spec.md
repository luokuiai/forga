## ADDED Requirements

### Requirement: Runnable Spring Boot authorization example
The repository MUST provide a runnable, non-published Spring Boot example that enables Forga and
declares the host-owned compiled policy, relationship resolver, and authenticated subject provider
required by starter auto-configuration.

#### Scenario: Example application starts
- **WHEN** the example application context starts
- **THEN** Spring assembles an `AuthorizationEvaluator` without missing-bean failures

#### Scenario: Declared relationship allows access
- **WHEN** the configured example subject requests the configured document
- **THEN** the protected endpoint returns the document response

#### Scenario: Missing identity is rejected
- **WHEN** a request does not provide the example identity header
- **THEN** the protected endpoint returns an unauthenticated response

#### Scenario: Missing relationship denies access
- **WHEN** an authenticated subject lacks the required relationship
- **THEN** the protected endpoint returns a forbidden response

### Requirement: Discoverable host configuration guidance
The README MUST link to the runnable example and MUST explain that `@EnableForga` requires a
host-owned `CompiledPolicy` and matching resolver beans, while applications that do not enable Forga
do not need those beans.

#### Scenario: User encounters a missing policy bean
- **WHEN** a user consults the Spring integration documentation
- **THEN** the documentation identifies the required bean, its purpose, and the runnable example
