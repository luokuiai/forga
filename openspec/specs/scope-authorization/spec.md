# scope-authorization Specification

## Purpose
TBD - created by archiving change add-scope-module. Update Purpose after archive.
## Requirements
### Requirement: Scope Value Objects

The SDK SHALL provide domain-neutral value objects for representing authorization scopes, active scope state, scoped subjects, and acting context.

#### Scenario: Create active scope

- **WHEN** a host application creates an active scope from a scope type and scope id
- **THEN** the SDK returns an immutable active scope that can be attached to request authorization state

#### Scenario: Reject invalid scope references

- **WHEN** a scope reference is created with a blank type or blank id
- **THEN** the SDK rejects the reference with an invalid argument error

### Requirement: Scope Switch Authorization

The SDK SHALL provide an authorization service method that checks whether a subject can enter a target scope using configured scope switch policy.

#### Scenario: Allowed switch

- **WHEN** the subject has the relationship or role required by the switch policy on the target scope
- **THEN** the scope switch check returns allowed and identifies the target active scope

#### Scenario: Denied switch

- **WHEN** the subject does not satisfy the switch policy for the target scope
- **THEN** the scope switch check returns denied and MUST NOT produce an active scope

#### Scenario: Switch check fails closed

- **WHEN** the resolver cannot complete the switch authorization check
- **THEN** the scope switch check returns a fail-closed denied result

### Requirement: Active Scope Permission Checks
The SDK SHALL provide a service method for checking permissions against the current active scope.
Every scoped service construction and object check MUST bind the protected object to a host-resolved
owning scope before evaluating the object permission.

#### Scenario: Allowed permission in matching active scope
- **WHEN** the subject may enter the active scope, the object's owning scope matches the active
  scope, and the subject has the requested object permission
- **THEN** the service returns allowed

#### Scenario: Missing active scope
- **WHEN** a scope-bound permission check is requested without an active scope
- **THEN** the service returns a fail-closed denied result

#### Scenario: Object scope is unresolved
- **WHEN** a scoped check cannot resolve the protected object's owning scope
- **THEN** the service returns denied and MUST NOT evaluate the object permission

#### Scenario: Permission isolated by scope
- **WHEN** the object's owning scope differs from the active scope and no explicit cross-scope grant
  exists
- **THEN** the service returns denied and MUST NOT evaluate the object permission

#### Scenario: Object scope resolver fails
- **WHEN** the object scope resolver fails while evaluating a scoped check
- **THEN** the service returns a fail-closed resolver-failure decision

### Requirement: Scope Policy Templates

The SDK SHALL provide reusable policy templates for membership, scoped role assignment, and explicit denial patterns.

#### Scenario: Membership template

- **WHEN** a host application builds a switch policy from the membership template
- **THEN** the resulting policy grants switch access to subjects related as members of the target scope

#### Scenario: Assignment template

- **WHEN** a host application builds a policy from the assignment template
- **THEN** the resulting policy grants switch access to subjects assigned in the target scope

### Requirement: Active Scope Providers

The SDK SHALL define provider contracts that integrations can use to obtain active scope and scoped request attributes.

#### Scenario: Provider returns active scope

- **WHEN** an integration asks the provider for active scope during a request
- **THEN** the provider returns the current active scope if one is available

#### Scenario: Provider missing active scope

- **WHEN** a scope-bound integration is configured but the provider returns no active scope
- **THEN** the integration fails closed for configured protected operations

### Requirement: Scoped Query Constraints

The SDK SHALL provide typed query constraints that bind protected queries to the active scope and SHALL allow composition with a host-provided set-based cross-scope grant constraint.

#### Scenario: Apply scope constraint

- **WHEN** a protected query is executed with an active scope
- **THEN** the generated query constraint includes parameterized predicates for the active scope fields

#### Scenario: Apply active-or-granted constraint

- **WHEN** a host provides a typed cross-scope grant constraint
- **THEN** the generated query constraint allows rows in the active scope or rows matched by the explicit grant constraint

#### Scenario: Disabled scope integration

- **WHEN** scope query integration is disabled
- **THEN** ordinary business queries execute unchanged

### Requirement: Explicit Cross-Scope Authorization

The SDK SHALL provide a host resolver contract for explicitly authorizing an object permission when the active scope differs from the object's owning scope.

#### Scenario: Cross-scope grant allows evaluation

- **WHEN** the subject may enter the active scope, the owning scope differs, and the cross-scope resolver explicitly allows the exact object permission request
- **THEN** the service evaluates the subject's ordinary object permission and returns that decision

#### Scenario: Cross-scope grant denied

- **WHEN** the owning scope differs and the cross-scope resolver does not explicitly allow the request
- **THEN** the service returns denied

#### Scenario: Cross-scope resolver fails

- **WHEN** the cross-scope resolver fails
- **THEN** the service returns a fail-closed resolver-failure decision

#### Scenario: Same-scope request avoids grant lookup

- **WHEN** the object's owning scope equals the active scope
- **THEN** the service proceeds without invoking the cross-scope resolver
