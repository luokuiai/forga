## Why

The MyBatis plugin currently rewrites a `BoundSql` instance that MyBatis does not necessarily
execute, and its string-based clause insertion can produce invalid or unsafe SQL for ordinary
SELECT shapes. Authorization constraints must be applied to the exact executable statement and
must fail closed when a statement cannot be transformed safely.

## What Changes

- Intercept the executable six-argument MyBatis query invocation and mutate its supplied
  `BoundSql` instance.
- Parse SELECT statements through the MyBatis-Plus JSqlParser integration before adding typed
  predicates, joins, projections, or ordering.
- Reject unsupported statement shapes and unsupported `IN` parameter operands before execution.
- Add real MyBatis integration tests that execute rewritten SQL and cover disabled and fail-closed
  behavior.
- Preserve host ownership of mapper statements, schemas, and parameter values.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `query-constraint-integration`: Require adapters to transform the executable query through a
  syntax-aware representation and reject unsupported SQL shapes.
- `mybatis-spring-auto-integration`: Require the auto-assembled plugin to constrain the exact
  `BoundSql` executed by MyBatis.

## Impact

The change affects `forga-mybatis`, its optional parser dependency, MyBatis integration tests, and
the Spring auto-integration contract. It does not change host business schemas or core policy and
resolver APIs. Statements that previously happened to pass through the string rewriter but cannot
be parsed safely will now fail closed when configured for authorization.
