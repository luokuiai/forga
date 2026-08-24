## MODIFIED Requirements

### Requirement: Automatic authorization evaluator assembly
When a host explicitly enables Forga, the Spring integration MUST assemble an
`AuthorizationEvaluator` from one host-owned `CompiledPolicy`, registered relationship resolvers,
and conservative default evaluation limits. Hosts MUST be able to replace the evaluator, lookup
adapters, resolver registry, caveat evaluator, and limits with their own Beans. Check-only assembly
MUST NOT require reverse resolver capabilities.

#### Scenario: Complete host runtime is enabled
- **WHEN** a host declares `@EnableForga`, one compiled policy, and forward resolvers supporting
  every relation required by that policy
- **THEN** exactly one `AuthorizationEvaluator` Bean is registered
- **AND** authorization checks use the host policy and resolver data

#### Scenario: Check-only host omits reverse capability
- **WHEN** an enabled host provides every required forward resolver but no reverse resolver
- **THEN** evaluator startup succeeds and `check` remains available
- **AND** a later `listObjects` call fails closed if its reverse capability is unavailable

#### Scenario: Host overrides the evaluator
- **WHEN** an enabled host provides its own `AuthorizationEvaluator` Bean
- **THEN** the Starter backs off and does not register another evaluator

#### Scenario: Enabled runtime lacks policy or forward resolver capability
- **WHEN** an enabled host omits its compiled policy or a forward resolver required by that policy
- **THEN** application startup fails before requests are served with a precise configuration error

#### Scenario: Integration is not enabled
- **WHEN** the Starter is present without `@EnableForga`
- **THEN** no evaluator, registry adapter, or evaluation limits Bean is registered by Forga
