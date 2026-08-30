## Why

Hosts currently declare relation and caveat capabilities when compiling a policy and repeat
relation and attribute declarations when registering resolvers. Attribute resolution is also
published as an SPI but is not consumed by the evaluator, forcing ABAC hosts either to preload
object state into every check or to perform unbounded work inside caveat code.

## What Changes

- **BREAKING** Compile policies from their immutable structure without caller-maintained
  `ResolverCapabilities`; validate policy requirements from the actual registered runtime.
- **BREAKING** Replace the bundled `RelationshipResolver` SPI with independently implementable
  forward relationship, reverse relationship, and attribute resolver capabilities under one
  neutral resolver registry.
- **BREAKING** Make caveat evaluators declare supported caveats and the object attributes required
  by each caveat.
- Add an evaluator attribute lookup contract and a registry adapter that batches object attribute
  reads, propagates deadline and consistency, and fails closed on malformed responses.
- Evaluate caveats against the current traversal object plus separately exposed request and
  resolver-owned object attributes.
- Batch attribute reads in `bulkCheck`, and apply object-dependent caveats to bounded candidates in
  `listObjects`.
- Keep roles, organizations, boundaries, appointments, and other host concepts outside Forga.
- Do not change scope authorization composition in this change; it will consume the completed
  attribute/runtime contract in a separate change.

## Capabilities

### New Capabilities

- `attribute-resolution`: Consistency-aware object attribute resolution and caveat evaluation for
  single checks, bulk checks, traversal, and bounded object listing.

### Modified Capabilities

- `authorization-evaluation`: Caveats declare their runtime requirements and evaluate the current
  object without host-side attribute preloading.
- `core-resolver-contracts`: Resolver capabilities become independently implementable and the
  registry becomes their authoritative runtime source.
- `relationship-resolution`: Forward, reverse, and attribute operations retain batching and
  consistency while no longer requiring one bundled resolver implementation.
- `authorized-object-listing`: Object-dependent caveats resolve attributes for bounded candidates
  instead of evaluating once against a synthetic listing object.
- `runtime-integration`: Spring assembly validates compiled policy requirements against registered
  resolvers and caveats without a duplicate compile-time capability list.

## Impact

The change affects `forga-core` policy compilation, caveat and resolver public APIs, evaluator
construction and caches, resolver fixtures, `forga-spring-boot-starter` assembly, and the runnable
example. Existing source implementations must migrate directly to the specialized resolver and
caveat contracts; no compatibility bridge is retained during the development phase.
