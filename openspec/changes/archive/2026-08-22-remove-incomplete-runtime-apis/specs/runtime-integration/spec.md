## ADDED Requirements

### Requirement: Runtime APIs correspond to assembled behavior
The Spring integration MUST expose only runtime types used by production assembly and MUST NOT
publish standalone request-scope or component-wrapper APIs that no integration consumes.

#### Scenario: Runtime beans are assembled
- **WHEN** Forga is enabled
- **THEN** hosts consume the typed policy, registry, lookups, limits, and evaluator beans directly

### Requirement: Focused authentication provider selection
MyBatis assembly MUST select exactly one authenticated subject provider and MUST report a focused
Forga configuration error when provider ownership is absent or ambiguous.

#### Scenario: Multiple providers exist
- **WHEN** an enabled MyBatis integration discovers more than one authenticated subject provider
- **THEN** startup fails with the Forga exactly-one-provider validation error
