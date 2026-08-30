## MODIFIED Requirements

### Requirement: Scope Switch Authorization

The SDK SHALL provide an authorization service method that checks whether a subject can enter a target scope using the service's single configured scope-entry permission. A switch request MUST NOT select a different permission.

#### Scenario: Allowed switch

- **WHEN** the subject has the relationship or role required by the configured switch policy on the target scope
- **THEN** the scope switch check returns allowed and identifies the target active scope

#### Scenario: Denied switch

- **WHEN** the subject does not satisfy the configured switch policy for the target scope
- **THEN** the scope switch check returns denied and MUST NOT produce an active scope

#### Scenario: Switch check fails closed

- **WHEN** the resolver cannot complete the switch authorization check
- **THEN** the scope switch check returns a fail-closed denied result

### Requirement: Active Scope Permission Checks
The SDK SHALL provide single and bounded bulk service methods for checking permissions against the current active scope. Every scoped service construction and object check MUST bind each protected object to a host-resolved owning scope before evaluating object permission. Bulk checks MUST batch distinct ownership, cross-scope grant, scope-entry, and object-permission work rather than invoke a host repository once per request.

#### Scenario: Allowed permission in matching active scope
- **WHEN** the subject may enter the active scope, the object's owning scope matches the active scope, and the subject has the requested object permission
- **THEN** the service returns allowed with the object-permission phase

#### Scenario: Missing active scope
- **WHEN** a scope-bound permission check is requested without an active scope
- **THEN** the service returns a fail-closed denial identifying active-scope validation

#### Scenario: Object scope is unresolved
- **WHEN** a scoped check cannot resolve the protected object's owning scope
- **THEN** the service returns denied identifying object ownership and MUST NOT allow the object

#### Scenario: Permission isolated by scope
- **WHEN** the object's owning scope differs from the active scope and no explicit cross-scope grant exists
- **THEN** the service returns denied identifying the cross-scope grant phase

#### Scenario: Boundary lookup fails
- **WHEN** an ownership or cross-scope batch is incomplete, malformed, or fails
- **THEN** affected requests fail closed without accepting partial results

#### Scenario: Bulk scope checks are set-oriented
- **WHEN** multiple scoped requests require ownership, cross-scope grants, or core permission evaluation
- **THEN** each distinct stage receives bounded batches rather than one host call per request

### Requirement: Explicit Cross-Scope Authorization

The SDK SHALL provide a complete host batch lookup for explicitly authorizing object permissions when active and owning scopes differ.

#### Scenario: Cross-scope grant allows evaluation

- **WHEN** the subject may enter the active scope, the owning scope differs, and the cross-scope lookup explicitly allows the exact object permission request
- **THEN** the service evaluates the subject's ordinary object permission and returns that decision

#### Scenario: Cross-scope grant denied

- **WHEN** the owning scope differs and the cross-scope lookup does not explicitly allow the request
- **THEN** the service returns denied in the cross-scope grant phase

#### Scenario: Cross-scope lookup fails

- **WHEN** the cross-scope lookup fails or returns an invalid batch
- **THEN** the service returns a fail-closed resolver-failure decision

#### Scenario: Same-scope request avoids grant lookup

- **WHEN** every object's owning scope equals its active scope
- **THEN** the service proceeds without invoking the cross-scope lookup
