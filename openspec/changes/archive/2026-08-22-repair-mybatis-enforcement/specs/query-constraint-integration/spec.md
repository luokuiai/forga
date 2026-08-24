## MODIFIED Requirements

### Requirement: Safe adapter translation
Persistence adapters MUST use allowlisted field mappings and parameter binding, MUST transform the
executable query through a syntax-aware representation, and MUST reject unknown fields, operators,
aliases, unsupported constraint nodes, or unsupported query shapes before execution.

#### Scenario: Unknown query field is supplied
- **WHEN** a constraint refers to a field not registered for that business query
- **THEN** translation fails closed before SQL execution

#### Scenario: Clause text appears inside a nested query or literal
- **WHEN** an adapter applies a constraint to a supported SELECT containing nested clause text
- **THEN** it modifies only the intended top-level SELECT structure

#### Scenario: Unsupported parameter operand is supplied
- **WHEN** a constraint uses an operator whose typed operand cannot be bound safely
- **THEN** translation fails closed before SQL execution
