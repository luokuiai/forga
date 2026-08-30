## MODIFIED Requirements

### Requirement: Core publishes resolver contracts
The `forga-core` artifact SHALL publish a neutral named resolver contract plus independently
implementable forward relationship, reverse relationship, and attribute resolver SPIs, their request
and response types, registry, bounds, pagination, failure, deadline, and consistency types under
`com.luokuiai.forga.resolver`.

#### Scenario: Host implements one resolver capability
- **WHEN** a host implements only forward relationship resolution with `forga-core` as its Forga dependency
- **THEN** it implements no reverse or attribute method and all required contract types remain available

#### Scenario: Host implements several capabilities
- **WHEN** one host adapter owns forward, reverse, and attribute reads from the same storage
- **THEN** one named class may implement all three operation interfaces and register once

### Requirement: Unique resolver capability ownership
The resolver registry MUST assign each forward relation, reverse relation, and attribute capability
to exactly one resolver operation and MUST reject duplicate resolver names, unsupported declarations,
and ambiguous capability ownership during construction.

#### Scenario: Two forward resolvers declare one relation
- **WHEN** differently named forward resolvers both declare support for the same relation
- **THEN** registry construction fails with both resolver names and the conflicting capability

#### Scenario: Resolver declares only attributes
- **WHEN** an attribute resolver is registered without a relationship resolver interface
- **THEN** the registry exposes only its declared attributes and does not invent relation capabilities

## REMOVED Requirements

### Requirement: Undeclared resolver operations default fail closed
**Reason**: Specialized resolver interfaces remove unrelated operations entirely, so default empty
reverse and attribute methods would recreate the bundled SPI being removed.

**Migration**: Implement only the forward, reverse, or attribute resolver interfaces actually owned
by the host adapter. Unsupported operations remain absent from the registry and fail closed there.
