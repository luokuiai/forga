## Context

`ForgaMyBatisAutoConfiguration` currently injects a registry, an attributes provider, and a raw
resource-mapping map. None is supplied by the starter, so an enabled host discovers missing
infrastructure through successive Spring startup failures. MyBatis is also optional at runtime even
though the starter exposes the framework-neutral integration module.

## Goals / Non-Goals

**Goals:**

- Make an enabled MyBatis integration start from individual statement and resource declarations.
- Supply safe empty defaults for derivable or optional components.
- Preserve host overrides and report ambiguous resource mappings at startup.
- Avoid activating MyBatis configuration when MyBatis is absent.

**Non-Goals:**

- Supplying an authenticated subject or changing authentication-provider validation.
- Inferring host SQL tables, columns, permissions, or statement authorization metadata.
- Changing SQL rewriting or authorization evaluation semantics.

## Decisions

The starter will construct a default `MyBatisStatementRegistry` from all ordered
`MyBatisStatementAuthorization` beans through `ObjectProvider`. An empty collection produces an
empty registry, preserving the existing behavior that unconfigured statements pass through
unchanged. A host-defined registry takes precedence through `@ConditionalOnMissingBean`.

The interceptor bean will collect `MyBatisResourceMapping` declarations directly and build the map
privately. This avoids making a generic `Map` a Spring extension contract and avoids collisions with
unrelated map beans. Duplicate `QueryResource` keys are rejected with a focused startup error.

The starter will provide `Map::of` as the default `AuthorizationAttributesProvider`. Hosts that use
ABAC request attributes can replace it with their own bean. No default is provided for
`AuthenticatedSubjectProvider`, because silently inventing an identity would weaken fail-closed
authorization; the existing exactly-one-provider validator remains authoritative.

The auto-configuration will use a name-based `@ConditionalOnClass` check for the MyBatis
`Interceptor` type. A name check avoids eagerly linking optional MyBatis classes while conditions
are evaluated.

## Risks / Trade-offs

- [An empty registry installs an interceptor with no protected statements] -> Preserve documented
  pass-through behavior and require explicit statement metadata for enforcement.
- [A missing resource mapping is only relevant when its boundary is translated] -> Keep host-owned
  schema declarations explicit; translation continues to fail closed when a required mapping is
  absent.
- [Default empty attributes can hide an omitted ABAC provider until execution] -> Missing named
  authorization parameters still fail closed in the SQL interceptor.
