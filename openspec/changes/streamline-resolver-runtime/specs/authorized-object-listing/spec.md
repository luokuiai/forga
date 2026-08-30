## MODIFIED Requirements

### Requirement: Complete permission-expression semantics
Object listing MUST support union, intersection, exclusion, subject sets, traversal, and caveats, or
reject a policy at validation when a required reverse or attribute capability is unavailable.
Object-dependent caveats MUST evaluate each bounded candidate using attributes resolved for that
concrete object under the listing consistency context.

#### Scenario: Intersection listing
- **WHEN** a permission requires membership in two relation branches
- **THEN** only objects proven by both branches are returned

#### Scenario: Listing caveat depends on object state
- **WHEN** a bounded relation branch produces candidate objects guarded by an object-state caveat
- **THEN** required attributes are batch-resolved for those candidates
- **AND** only candidates whose caveat passes are returned

#### Scenario: Listing lacks required attribute capability
- **WHEN** a listing caveat declares an object attribute with no registered resolver
- **THEN** listing fails closed without returning a partial candidate set
