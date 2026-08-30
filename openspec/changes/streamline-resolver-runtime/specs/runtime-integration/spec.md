## MODIFIED Requirements

### Requirement: Automatic authorization evaluator assembly
When a host explicitly enables Forga and supplies one host-owned `CompiledPolicy`, Spring MUST
assemble an `AuthorizationEvaluator` from that policy, registered specialized resolvers, a caveat
evaluator, an optional host permission grant lookup, and conservative default evaluation limits.
Assembly MUST derive relation, caveat, attribute, and grant requirements from the compiled policy
and actual runtime registrations without a caller-maintained capability list. When the policy is
absent, the integration MUST leave the evaluator absent, MUST allow unrelated application
components to start, and MUST emit a startup warning that evaluator-based authorization is inactive.
Hosts MUST be able to replace the evaluator, grant lookup, lookup adapters, resolver registry,
caveat evaluator, and limits with their own Beans. Check-only assembly MUST NOT require reverse
resolver capabilities.

#### Scenario: Complete host runtime is enabled
- **WHEN** a host declares `@EnableForga`, one compiled policy, and every runtime capability required
  by that policy
- **THEN** exactly one `AuthorizationEvaluator` Bean is registered
- **AND** authorization checks use the host policy and resolver data

#### Scenario: Grant policy has a host lookup
- **WHEN** an enabled compiled policy contains a grant expression and a `PermissionGrantLookup` Bean
  is present
- **THEN** the assembled evaluator delegates grant leaves to that lookup

#### Scenario: Grant policy lacks a host lookup
- **WHEN** an enabled compiled policy contains a grant expression without a `PermissionGrantLookup` Bean
- **THEN** startup fails with a precise missing grant lookup configuration error

#### Scenario: Enabled host has no policy yet
- **WHEN** a host declares `@EnableForga` without a `CompiledPolicy` or custom evaluator
- **THEN** application startup succeeds without an `AuthorizationEvaluator` Bean
- **AND** one startup warning states that evaluator-based authorization is inactive

#### Scenario: Unconfigured host component requires evaluator
- **WHEN** an enabled host has no policy but declares a component that requires an `AuthorizationEvaluator`
- **THEN** Spring dependency validation fails instead of injecting an allow-all implementation

#### Scenario: Check-only host omits reverse capability
- **WHEN** an enabled host provides every required check lookup but no reverse resolver
- **THEN** evaluator startup succeeds and `check` remains available
- **AND** a later `listObjects` call fails closed if its reverse capability is unavailable

#### Scenario: Host overrides the evaluator
- **WHEN** an enabled host provides its own `AuthorizationEvaluator` Bean
- **THEN** the Starter backs off and does not register another evaluator or an inactive warning

#### Scenario: Enabled runtime lacks forward resolver capability
- **WHEN** an enabled host supplies a policy but omits a forward resolver required by that policy
- **THEN** application startup fails before requests are served with a precise configuration error

#### Scenario: Enabled runtime lacks caveat attribute capability
- **WHEN** a policy uses a registered caveat whose required object attribute has no resolver
- **THEN** application startup fails with the caveat and missing attribute in the configuration error

#### Scenario: Integration is not enabled
- **WHEN** the Starter is present without `@EnableForga`
- **THEN** no evaluator, registry adapter, or evaluation limits Bean is registered by Forga

### Requirement: Resolver registry evaluation bridge
The runtime integration MUST adapt forward, reverse, and attribute resolver batches to evaluator
lookup contracts without per-result queries, MUST preserve pagination, deadline, and consistency
state, and MUST fail closed when capabilities or resolver responses are incomplete or malformed.

#### Scenario: Forward requests span resolver capabilities
- **WHEN** one evaluator lookup batch contains requests handled by multiple forward resolvers
- **THEN** requests are grouped by resolver and each resolver receives one bounded batch
- **AND** every returned direct subject and subject set is converted to an evaluator relationship entry

#### Scenario: Reverse resolver returns a continuation
- **WHEN** a reverse resolver returns a bounded object page with a cursor and consistency token
- **THEN** the evaluator listing page contains the same objects, cursor, and consistency token

#### Scenario: Attribute requests span resolver capabilities
- **WHEN** one evaluator lookup batch requests object attributes handled by multiple resolvers
- **THEN** requests are grouped into bounded resolver batches and merged without per-object queries

#### Scenario: Resolver response is malformed
- **WHEN** a resolver returns a missing, duplicate, extra, null, or mismatched batch response
- **THEN** the lookup raises a structured resolver failure and authorization fails closed
