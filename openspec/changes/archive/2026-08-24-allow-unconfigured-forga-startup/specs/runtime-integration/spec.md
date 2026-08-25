## MODIFIED Requirements

### Requirement: Opt-in runtime assembly
Framework integrations MUST register authorization components only when a host composition root
explicitly declares `@EnableForga`. Environment properties, including `forga.enabled`, MUST NOT
enable or disable Forga assembly. Annotation-enabled integration MUST NOT require a separate runtime
properties bean carrying another enablement flag. Enabled integration MUST validate every supplied
policy, resolver capability, authentication provider, and duplicate registration before serving
requests.

#### Scenario: Composition root enables Forga
- **WHEN** a host configuration class declares `@EnableForga`
- **THEN** Forga integration components and the versioned startup banner are registered
- **AND** invalid supplied infrastructure causes application startup to fail with a precise
  configuration error
- **AND** no separate integration enablement properties bean is required

#### Scenario: Legacy property attempts to enable Forga
- **WHEN** the Starter is present and `forga.enabled=true` is configured without `@EnableForga`
- **THEN** Forga integration components and the startup banner MUST remain absent

### Requirement: Automatic authorization evaluator assembly
When a host explicitly enables Forga and supplies one host-owned `CompiledPolicy`, Spring MUST
assemble an `AuthorizationEvaluator` from that policy, registered relationship
resolvers, and conservative default evaluation limits. When the policy is absent, the integration
MUST leave the evaluator absent, MUST allow unrelated application components to start, and MUST emit
a startup warning that evaluator-based authorization is inactive. Hosts MUST be able to replace the
evaluator, lookup adapters, resolver registry, caveat evaluator, and limits with their own Beans.
Check-only assembly MUST NOT require reverse resolver capabilities.

#### Scenario: Complete host runtime is enabled
- **WHEN** a host declares `@EnableForga`, one compiled policy, and forward resolvers supporting
  every relation required by that policy
- **THEN** exactly one `AuthorizationEvaluator` Bean is registered
- **AND** authorization checks use the host policy and resolver data

#### Scenario: Enabled host has no policy yet
- **WHEN** a host declares `@EnableForga` without a `CompiledPolicy` or custom evaluator
- **THEN** application startup succeeds without an `AuthorizationEvaluator` Bean
- **AND** one startup warning states that evaluator-based authorization is inactive

#### Scenario: Unconfigured host component requires evaluator
- **WHEN** an enabled host has no policy but declares a component that requires an
  `AuthorizationEvaluator`
- **THEN** Spring dependency validation fails instead of injecting an allow-all implementation

#### Scenario: Check-only host omits reverse capability
- **WHEN** an enabled host provides every required forward resolver but no reverse resolver
- **THEN** evaluator startup succeeds and `check` remains available
- **AND** a later `listObjects` call fails closed if its reverse capability is unavailable

#### Scenario: Host overrides the evaluator
- **WHEN** an enabled host provides its own `AuthorizationEvaluator` Bean
- **THEN** the Starter backs off and does not register another evaluator or an inactive warning

#### Scenario: Enabled runtime lacks forward resolver capability
- **WHEN** an enabled host supplies a policy but omits a forward resolver required by that policy
- **THEN** application startup fails before requests are served with a precise configuration error

#### Scenario: Integration is not enabled
- **WHEN** the Starter is present without `@EnableForga`
- **THEN** no evaluator, registry adapter, or evaluation limits Bean is registered by Forga
