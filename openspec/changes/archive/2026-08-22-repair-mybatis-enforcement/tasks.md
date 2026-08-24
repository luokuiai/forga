## 1. Executable Query Enforcement

- [x] 1.1 Cover both MyBatis query entry points and rewrite the executable `BoundSql`
- [x] 1.2 Add a real MyBatis execution test proving the database receives the constraint

## 2. Syntax-Aware Translation

- [x] 2.1 Add the MyBatis-Plus JSqlParser dependency and replace raw clause searches with AST edits
- [x] 2.2 Reject unsupported SELECT shapes and unsupported `IN` operands before execution
- [x] 2.3 Add translator tests for trailing clauses, nested SQL, literals, semicolons, and failures

## 3. Verification

- [x] 3.1 Run focused MyBatis tests and Checkstyle
- [x] 3.2 Validate the OpenSpec change strictly
- [x] 3.3 Run the repository-wide clean check
