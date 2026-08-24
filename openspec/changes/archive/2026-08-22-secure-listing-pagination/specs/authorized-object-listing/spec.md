## MODIFIED Requirements

### Requirement: Stable cursor pagination
Object listing MUST use authenticated opaque stable cursors bound to subject, object type,
permission, policy fingerprint, request attributes, consistency context, and resolver continuation
state. It MUST consume the current resolver result page before applying its next continuation.

#### Scenario: Cursor is reused with another permission
- **WHEN** a caller submits a cursor with request parameters different from those that created it
- **THEN** the engine rejects the cursor without returning results

#### Scenario: Cursor is modified
- **WHEN** any byte of an issued cursor is modified
- **THEN** the engine rejects the cursor without exposing or accepting its state

#### Scenario: Engine page is smaller than resolver page
- **WHEN** one resolver page contains more authorized objects than the requested engine page
- **THEN** subsequent pages return every remaining object before advancing the resolver continuation

#### Scenario: Resolver continuation advances
- **WHEN** the current resolver page has been fully consumed and it provides a continuation
- **THEN** the next engine page starts at offset zero using that continuation

#### Scenario: Final page is returned
- **WHEN** no additional authorized objects remain
- **THEN** the result has no continuation cursor and contains no duplicate object references
