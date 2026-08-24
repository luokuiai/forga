## MODIFIED Requirements

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
