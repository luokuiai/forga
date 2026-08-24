## MODIFIED Requirements

### Requirement: Statement metadata driven authorization
The integration MUST apply authorization only at statement ids explicitly registered with a typed
authorization boundary. Statement declarations MUST contain only metadata consumed by enforcement.

#### Scenario: Configured statement is authorized
- **WHEN** a configured MyBatis statement executes while Forga is enabled
- **THEN** the integration applies one composed typed authorization constraint for that statement

#### Scenario: Unconfigured statement executes
- **WHEN** a statement id has no authorization metadata
- **THEN** the integration leaves the SQL unchanged
