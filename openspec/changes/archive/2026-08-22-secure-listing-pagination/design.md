## Context

An engine response page can be smaller than the page returned by a reverse resolver. The current
cursor stores the offset into that resolver page but also stores the resolver's next continuation,
so the next request applies the old offset to a different page. Cursor payloads are plain Base64
and expose host resolver tokens.

## Goals / Non-Goals

**Goals:**

- Return every object from a resolver page before advancing its continuation.
- Detect cursor modification and conceal request and resolver continuation state.
- Preserve existing bounds, consistency checks, deterministic ordering, and resolver APIs.

**Non-Goals:**

- Making cursors durable across evaluator recreation or application restart.
- Introducing a distributed cursor store or host key-management property.
- Removing resolver stability requirements while replaying the same continuation page.

## Decisions

1. Cursor state distinguishes the resolver input continuations from output continuations collected
   during evaluation. While the local offset has not consumed the current result set, the cursor
   retains the input continuations. Once consumed, it resets the offset and advances to the output
   continuations.
2. The evaluator replays the same bounded resolver page while serving multiple engine pages. This
   avoids serializing object buffers into cursors and preserves the existing stateless API. A
   resolver must provide stable results for a continuation under the established consistency token.
3. Cursor payloads use AES-256-GCM with a random 96-bit nonce and an evaluator-instance key created
   through JCA `SecureRandom`. GCM provides confidentiality and integrity without another
   dependency. Any decode or authentication failure becomes `INVALID_CURSOR`.
4. Cursor format advances to version 2. Version 1 cursors are rejected rather than accepted through
   an insecure compatibility path.

## Risks / Trade-offs

- [Resolver calls repeat while consuming one resolver page] -> Calls remain bounded by request
  limits and use the same consistency token; avoiding replay would require buffered or oversized
  cursors.
- [Cursors fail after evaluator recreation] -> Document instance-scoped validity; a future host-key
  codec can be introduced if cross-instance pagination becomes a requirement.
- [JVM lacks AES-GCM] -> Java 17 requires this algorithm; initialization failure stops evaluator
  construction rather than silently weakening cursor protection.

## Migration Plan

Deploying this version invalidates outstanding list cursors. Clients restart pagination when they
receive `INVALID_CURSOR`. Resolver and request APIs do not change.

## Open Questions

Cross-instance key management remains a future capability and must be explicit rather than an
application property hidden inside core evaluation.
