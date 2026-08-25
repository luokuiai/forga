# core-resolver-contracts Specification

## Purpose
TBD - created by archiving change merge-resolver-api-into-core. Update Purpose after archive.
## Requirements
### Requirement: Core publishes resolver contracts
The `forga-core` artifact SHALL publish the host resolver SPI, resolver request and response types,
registry, bounds, pagination, failure, deadline, and consistency types under their existing
`com.luokuiai.forga.resolver` packages.

#### Scenario: Host compiles against core only
- **WHEN** a host implements `RelationshipResolver` with only `forga-core` as its Forga dependency
- **THEN** all resolver contract and value types are available without a resolver-specific artifact

### Requirement: Resolver behavior remains compatible
Moving resolver APIs into core MUST preserve forward, reverse, attribute, batching, pagination,
deadline, failure, and consistency behavior.

#### Scenario: Existing resolver implementation migrates
- **WHEN** a host replaces its resolver artifact dependency with `forga-core`
- **THEN** its existing `com.luokuiai.forga.resolver` imports and implementation behavior remain valid

### Requirement: Core publishes resolver contract fixtures
The `forga-core` test fixtures SHALL provide the reusable resolver contract suite for host resolver
implementations.

#### Scenario: Host verifies resolver conformance
- **WHEN** a host consumes the core test fixtures and runs the resolver contract suite
- **THEN** bounded responses, stable cursor state, and consistency behavior are verified

### Requirement: Standalone resolver artifact is removed
The build MUST NOT include or publish a `forga-resolver-api` project after resolver contracts move
into core.

#### Scenario: SDK modules resolve dependencies
- **WHEN** the complete multi-module build resolves project dependencies
- **THEN** no module depends on `forga-resolver-api` and resolver users depend on `forga-core`

### Requirement: Unique resolver capability ownership
The resolver registry MUST assign each forward relation, reverse relation, and attribute capability
to exactly one resolver and MUST reject ambiguous declarations during construction.

#### Scenario: Two resolvers declare one forward relation
- **WHEN** differently named resolvers both declare forward support for the same relation
- **THEN** registry construction fails with both resolver names and the conflicting capability

### Requirement: Resolver deadline propagation
Context-aware evaluator lookup adapters MUST propagate the evaluation deadline to every forward and
reverse resolver request in the batch.

#### Scenario: Evaluation has a timeout
- **WHEN** an evaluator performs relationship resolution with a configured timeout
- **THEN** every resolver request receives the same absolute deadline for that evaluation

### Requirement: Undeclared resolver operations default fail closed
`RelationshipResolver` SHALL provide complete empty default responses for reverse and attribute
operations so hosts implementing only declared forward capabilities do not need unrelated method
boilerplate. Default responses MUST preserve each submitted request and its consistency context.

#### Scenario: Forward-only resolver omits optional methods
- **WHEN** a host declares only forward relation capabilities and implements descriptor and forward
  resolution
- **THEN** the resolver compiles without host implementations for reverse or attribute resolution
- **AND** direct default calls return one empty response for every submitted request

#### Scenario: Undeclared default operation receives a batch
- **WHEN** a reverse or attribute batch is passed to the corresponding default method
- **THEN** the result contains exactly one response per request in request order
- **AND** no objects or attributes are granted
- **AND** request consistency is preserved
