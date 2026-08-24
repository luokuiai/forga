## Why

Several public development-stage APIs imply guarantees that their production paths do not provide:
scope constructors can skip object ownership, statement metadata carries unused authorization
fields, and Spring exposes request/runtime wrappers used only by their own tests. Proof collection
also retains evidence from failed boolean branches.

## What Changes

- **BREAKING** Remove scoped service constructors that do not require object ownership resolution.
- **BREAKING** Reduce MyBatis statement metadata to statement id and its enforced typed boundary.
- **BREAKING** Remove unused public Spring request-scope and runtime-component wrapper APIs.
- Keep resolver validation as package-private Spring assembly support.
- Roll back proof steps from failed union, intersection, exclusion, traversal, and subject-set paths.
- Select exactly one authentication provider inside MyBatis assembly so startup errors remain
  Forga-specific.
- Correct README examples and attribute-resolution claims.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `scope-authorization`: Require every scoped object check to resolve object ownership.
- `mybatis-spring-auto-integration`: Make statement declarations describe only enforced behavior.
- `runtime-integration`: Remove unused request and component assembly wrappers.
- `authorization-evaluation`: Require successful proofs to contain only evidence on successful paths.

## Impact

Hosts using pre-release compatibility constructors or wrapper records must move to strict scope
construction and direct Spring beans. MyBatis statement declarations drop two unused arguments.
Authorization decisions do not change, but proof output becomes accurate.
