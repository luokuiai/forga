## Why

Object listing currently advances a resolver continuation while retaining an offset into the prior
resolver page, which can skip authorized objects. Its Base64 cursor is also readable and editable,
so callers can tamper with pagination and embedded resolver state.

## What Changes

- Keep the resolver input continuation stable while an engine page is being consumed, then advance
  to the resolver output continuation with a reset offset.
- Protect cursor payloads with authenticated encryption and reject modified tokens.
- Preserve cursor binding to policy, request identity, attributes, consistency, and resolver state.
- Add multi-page continuity, tampering, and consistency tests.
- Document that default cursor keys are evaluator-instance scoped.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `authorized-object-listing`: Strengthen stable cursor pagination with continuous resolver-page
  consumption and authenticated opaque cursor state.

## Impact

The change affects core object-listing cursor state and tests. Resolver contracts and host business
data ownership remain unchanged. Cursors produced by older versions are intentionally invalidated.
