## Context

MyBatis exposes both four-argument and six-argument `Executor.query` methods. The current plugin
intercepts the four-argument method and obtains a new `BoundSql`, but MyBatis obtains another
instance before executing the query. Separately, Forga inserts clauses by searching raw SQL text,
which cannot distinguish top-level clauses from nested queries, comments, or literals.

## Goals / Non-Goals

**Goals:**

- Modify the exact `BoundSql` instance passed to the executable query invocation.
- Use a SQL AST for clause placement and reject statements that cannot be transformed safely.
- Preserve bound parameters and disabled/unconfigured behavior.
- Prove enforcement with a real MyBatis execution test.

**Non-Goals:**

- Supporting every vendor-specific SELECT syntax.
- Authorizing INSERT, UPDATE, DELETE, or stored procedure statements.
- Changing host mapper APIs, schemas, or the typed query-constraint model.
- Implementing collection-valued `IN` operands in this change.

## Decisions

1. The plugin covers both `Executor.query` signatures. For the ordinary four-argument entry point,
   it replaces the invocation's `MappedStatement` with an equivalent statement whose `SqlSource`
   returns the authorized `BoundSql`; this survives MyBatis's internal self-invocation of the
   six-argument overload. A direct six-argument invocation rewrites `args[5]` in place. Each path
   therefore changes the exact object consumed by the executor without applying twice.
2. `MyBatisConstraintTranslator` uses the JSqlParser version supplied by
   `mybatis-plus-jsqlparser`. Forga remains aligned with its existing MyBatis-Plus version instead
   of independently selecting a parser version.
3. Only one plain SELECT body is accepted. Parse failures, set operations or unsupported AST shapes
   raise `MyBatisTranslationException` before database execution. Nested SELECTs may remain in the
   host query because top-level clause changes are performed on the AST.
4. Authorization expressions remain typed and allowlisted before becoming parser expressions.
   Parameter placeholders remain MyBatis placeholders and values continue to be attached as
   `BoundSql` additional parameters.
5. `PredicateOperator.IN` is rejected until the query model represents a typed collection operand
   and the adapter can emit one placeholder per element. Emitting `IN #{parameter}` is not valid
   portable SQL.

## Risks / Trade-offs

- [Parser normalization can change SQL formatting] -> Tests assert behavior and relevant clauses,
  not host-provided whitespace as an API contract.
- [A dialect extension may not parse] -> Configured statements fail closed; hosts can leave an
  unsupported statement unregistered or provide a supported mapper query.
- [Parser dependency increases adapter size] -> Keep it inside the optional MyBatis module and use
  the version managed alongside MyBatis-Plus.
- [Reflective mutation depends on MyBatis internals] -> Limit reflection to the final `BoundSql`,
  exercise it against the pinned MyBatis version, and fail closed on mutation failure.

## Migration Plan

Existing configured mapper statements should be exercised during upgrade. Queries accepted by the
old string rewriter but rejected by the parser must be simplified or left unregistered until their
dialect shape is supported. Disabling Forga or leaving a statement unregistered preserves the
original SQL.

## Open Questions

None for this change. Collection operands and additional dialect support require separate typed
contracts and tests.
