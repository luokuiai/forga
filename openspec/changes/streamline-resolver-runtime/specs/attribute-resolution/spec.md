## ADDED Requirements

### Requirement: Caveats declare object attribute requirements
Each caveat evaluator MUST declare every supported caveat and the object attributes required by that
caveat. Caveat evaluation MUST receive the current evaluation object and subject, original check
request, request attributes, and resolver-owned object attributes as distinct values.

#### Scenario: Caveat follows a traversal
- **WHEN** a caveat is evaluated after a relation traversal
- **THEN** its object attributes are resolved for the current traversed object
- **AND** its context still identifies the original protected request

#### Scenario: Request attribute collides with object attribute
- **WHEN** the same attribute reference exists in caller request attributes and resolved object attributes
- **THEN** the caveat receives both sources separately and no caller value overwrites resolved state

### Requirement: Attribute reads are bounded and consistency-aware
The evaluator MUST resolve required attributes through bounded batch lookups using the current
evaluation deadline and consistency token. It MUST cache immutable results within the operation and
MUST reject conflicting returned tokens.

#### Scenario: Bulk checks require the same object attributes
- **WHEN** several bulk decisions reach caveats whose object attributes are known at one frontier
- **THEN** missing attribute requests are sent in a bounded batch instead of one read per decision

#### Scenario: Attribute read establishes consistency
- **WHEN** an attribute resolver establishes a consistency token
- **THEN** later relationship, grant, reverse, and attribute reads in the operation receive that token

### Requirement: Attribute resolution fails closed
Attribute lookup batches MUST return one non-null result for each submitted request and MUST reject
missing, extra, duplicate, unexpected, or conflicting data. A requested attribute with no stored
value MAY be absent from an otherwise valid result and MUST NOT be treated as granted.

#### Scenario: Resolver omits an attribute request
- **WHEN** an attribute resolver omits a submitted request from its batch response
- **THEN** the dependent decision is denied with a stable resolver failure reason

#### Scenario: Requested attribute has no value
- **WHEN** a complete response contains no value for a requested attribute
- **THEN** the caveat sees that attribute as absent and decides without an implicit allow

### Requirement: Attribute resolvers preserve host ownership
Attribute resolver contracts MUST use only opaque object and attribute references and values. Hosts
MUST retain ownership of attribute storage, loading, caching, and interpretation.

#### Scenario: Host resolves workflow state
- **WHEN** a host maps a business object state to an opaque attribute reference and value
- **THEN** Forga batches and transports the value without interpreting the business state
