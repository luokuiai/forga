# authorization-evaluation Specification

## Purpose
TBD - created by archiving change build-embedded-authorization-engine. Update Purpose after archive.
## Requirements
### Requirement: Domain-neutral authorization model
The engine MUST model subjects, objects, relations, permissions, caveats, and attributes without
reserving or interpreting host-domain object types.

#### Scenario: Host defines a hierarchy
- **WHEN** a host defines its own object type and parent relation
- **THEN** the engine evaluates that relation without any built-in tenant semantics

### Requirement: Opaque public references
Forga public APIs and core models MUST expose only opaque subject, object, relation, permission,
attribute, caveat, cursor, and consistency references. They MUST NOT expose tenant ids,
`TenantSubject`, host access-path names, or host-domain user, person, organization, workflow,
meeting, todo, appointment, proxy, mapped, or borrowed concepts.

#### Scenario: Host encodes tenant context in an id
- **WHEN** a host passes a subject or object id containing tenant, organization, or other business
  segments
- **THEN** Forga treats the id as an opaque value for equality, routing, cursor binding, and
  diagnostics
- **AND** Forga does not parse, normalize, or infer authorization semantics from those segments

#### Scenario: Host-specific concept is considered for SDK APIs
- **WHEN** an implementation adds or adapts authorization mechanics for the SDK
- **THEN** reusable mechanics such as typed constraints, fail-closed validation, disabled behavior,
  and batching MAY be implemented
- **AND** host-domain semantics MUST remain outside standalone Forga APIs and core models

### Requirement: Composable permission evaluation
The engine MUST evaluate direct relations, subject sets, union, intersection, exclusion, relation
traversal, and caveats from one immutable policy model. An allowed decision proof MUST contain only
steps belonging to a successful expression path.

#### Scenario: Permission traverses a parent relation
- **WHEN** a permission grants access through a parent object relation and the subject is related to
  that parent
- **THEN** `check` returns an allowed decision with only the successful traversed proof

#### Scenario: Failed union branch precedes an allowed branch
- **WHEN** an earlier union branch records relationship steps but fails and a later branch succeeds
- **THEN** the allowed proof excludes every step from the failed branch

#### Scenario: Excluded subject is denied
- **WHEN** a subject satisfies the base expression and also satisfies its exclusion expression
- **THEN** `check` returns a denied decision

### Requirement: Consistent single and bulk checks
`check` and `bulkCheck` MUST produce equivalent decisions for identical inputs. `bulkCheck` MUST
group distinct relationship requests at each graph frontier into bounded resolver batches,
including frontiers discovered through subject sets and traversal, instead of issuing one resolver
query per object. Each bulk decision MUST own independent visited-node, logical resolver-call,
intermediate-result, cycle, proof, failure, and timeout state; bulk requests MAY share only
operation-local immutable resolver results used to avoid duplicate physical lookups.

#### Scenario: Batch matches individual checks
- **WHEN** the same checks are evaluated individually and in one batch under the same context
- **THEN** every decision and failure reason is equivalent

#### Scenario: Distinct objects share a frontier
- **WHEN** a bulk check evaluates one relation on multiple distinct objects
- **THEN** the resolver receives those relationship requests in one bounded batch

#### Scenario: Traversal discovers another frontier
- **WHEN** a batched traversal resolves multiple intermediate subject-set objects
- **THEN** the nested relation requests for those objects are sent as the next bounded batch

#### Scenario: Tight limits are isolated per decision
- **WHEN** multiple checks each fit independently within configured limits
- **THEN** evaluating them in one batch does not consume another decision's limits or deadline

#### Scenario: Prefetch does not bypass logical resolver limits
- **WHEN** one check requires more distinct relationship lookups than its resolver-call limit
- **THEN** the bulk decision fails with the same limit reason as an individual check even if all
  relationship results were physically prefetched

### Requirement: Bounded fail-closed evaluation
The evaluator MUST enforce configured depth, visited-node, resolver-call, intermediate-result,
batch-size, and per-evaluation timeout limits, MUST derive a fresh absolute deadline for every
evaluation, and MUST detect active-path cycles.

#### Scenario: Cyclic relationship graph
- **WHEN** evaluation encounters a relation cycle without another valid proof
- **THEN** the request terminates without recursion overflow and returns a denied bounded result

#### Scenario: Evaluation limit exceeded
- **WHEN** any configured evaluation limit is exceeded
- **THEN** the engine stops evaluation and returns a structured fail-closed result

#### Scenario: Singleton evaluator handles later request
- **WHEN** two requests begin at different times with the same configured timeout duration
- **THEN** each request receives a deadline derived from its own evaluation start

### Requirement: Observable decisions
The engine MUST expose stable decision reason codes and metrics for latency, resolver calls, cache
hits, evaluated nodes, limit failures, and allowed or denied outcomes without recording sensitive
attribute values.

#### Scenario: Decision completes
- **WHEN** an authorization decision completes
- **THEN** metrics include its outcome and evaluation cost while logs omit sensitive context values

