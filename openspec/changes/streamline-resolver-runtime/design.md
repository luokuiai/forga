## Context

Forga has two sources of runtime capability truth. `PolicyCompiler` requires hosts to construct a
`ResolverCapabilities` value, while `ResolverRegistry` independently reads `ResolverDescriptor`.
The bundled `RelationshipResolver` also exposes forward, reverse, and attribute methods even when a
host implements only one capability. Finally, attribute transport contracts exist but the evaluator
never invokes them; caveats receive only the original `CheckRequest`, including during traversal and
object listing.

Complex hosts need all three resolver directions. They also need object-state ABAC without loading
every attribute in controllers or issuing one caveat query per object. The change therefore removes
duplicate declarations while completing, rather than deleting, the attribute path.

## Goals / Non-Goals

**Goals:**

- Make registered runtime components the only capability source.
- Let one implementation provide any combination of forward, reverse, and attribute capabilities
  without unrelated methods.
- Resolve caveat object attributes in bounded batches with the evaluation deadline and consistency
  token.
- Give caveats the original check tuple, current traversal object and subject, request attributes,
  and separately identified resolver-owned object attributes.
- Preserve fail-closed behavior, per-decision limits, and single/bulk equivalence.

**Non-Goals:**

- Defining attribute types or interpreting attribute values in core.
- Owning host data, snapshots, roles, boundaries, or caches.
- Turning graph listing into the default implementation for business SQL pages.
- Refactoring `forga-scope` decision composition in this change.
- Providing compatibility overloads for replaced development-stage APIs.

## Decisions

### Compile policy structure without runtime capability input

`PolicyCompiler.compile(PolicyDefinition)` validates the expression structure and computes its
fingerprint. A core runtime validator traverses the compiled policy during assembly and verifies
relations against `ResolverRegistry`, caveats against `CaveatEvaluator`, required attributes against
registered attribute resolvers, and grant leaves against the configured grant lookup.

This removes caller-maintained capability lists. Keeping both lists and comparing them was rejected
because it preserves the integration ceremony and drift risk.

### Split resolver operations under one named registry contract

Introduce a neutral named resolver base and independent forward, reverse, and attribute resolver
interfaces. Each operation interface declares only its own supported references and batch method. A
single host class may implement multiple interfaces. `ResolverRegistry` indexes the implemented
interfaces and rejects duplicate names or capability ownership.

Separate descriptors per operation were rejected because they replace one duplicated declaration
with several carrier objects. Keeping default no-op methods was rejected because unsupported
operations remain visible on every implementation.

### Make caveat requirements explicit

`CaveatEvaluator` declares supported caveats and required object attributes per caveat. Evaluation
receives a `CaveatEvaluationContext` containing the original request, current object and subject,
request attributes, and resolver-owned object attributes. These maps remain separate so caller
input cannot impersonate authoritative object state through a key collision.

Attribute values remain opaque strings. A typed expression language is deferred until concrete host
requirements justify it.

### Add an evaluator attribute lookup and registry adapter

The evaluator uses `AttributeLookupRequest` and `AttributeLookup`, returning the same
consistency-aware `BatchResolution` shape as relationship and grant lookups. The registry adapter
splits requested attributes by owning resolver, sends bounded batches, validates complete responses,
merges partial attribute maps per object request, and propagates a token sequentially across physical
batches.

Missing attribute values are a valid result and cause caveat code to deny unless it explicitly
accepts absence. Missing resolver capabilities, unexpected attributes, duplicate values, malformed
batches, exceptions, deadline exhaustion, and token conflicts fail closed.

### Prefetch attributes at each known bulk frontier

Bulk expansion records caveat attribute requests for each currently known object. It resolves all
missing requests as one bounded logical batch before evaluating caveats and continuing through their
guarded expressions. Traversal-created objects enter a later frontier and are batched there. Shared
physical results seed independent decision state, matching relationship and grant prefetch behavior.

### Filter bounded listing candidates for object caveats

For a caveat expression, listing first obtains the bounded candidate set from the guarded expression,
then resolves required attributes for those concrete objects as a batch and evaluates the caveat per
candidate. Request-only caveats use the same path with no attribute lookup. The resolver consistency
token becomes part of the existing listing cursor state.

## Risks / Trade-offs

- [Breaking public APIs] -> Migrate every core test, fixture, Starter test, and example in the same
  change; no compatibility facade remains.
- [Evaluator complexity increases] -> Keep attribute caching and validation parallel to existing
  relationship/grant patterns and cover single, bulk, traversal, listing, and failure paths.
- [Caveat implementations become non-lambda interfaces] -> Gain explicit capability and dependency
  declarations, enabling startup validation and batch planning.
- [One logical attribute request may span resolvers] -> Split only inside the registry adapter and
  expose one merged evaluator result, preserving host resolver ownership.
- [Listing caveats can reduce a page] -> Evaluate only bounded candidates and retain existing cursor
  and continuation limits; never scan a host business table.

## Migration Plan

1. Replace `PolicyCompiler.compile(definition, capabilities)` with `compile(definition)`.
2. Migrate resolver Beans to the specialized interfaces and direct capability methods.
3. Migrate caveat implementations to declare caveats and required object attributes.
4. Register the attribute lookup adapter in Spring and pass it to the evaluator.
5. Run resolver contract fixtures, focused module checks, and the complete Gradle check.

Rollback during development is a source rollback; no host data migration is introduced.

## Open Questions

None for this change. Typed attribute values and scope policy composition remain separate design
decisions.
