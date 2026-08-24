## Why

Enabling Forga currently makes Spring require hosts to manually construct internal MyBatis
infrastructure beans, causing startup failures one dependency at a time. The starter should assemble
derivable infrastructure while keeping authentication and host-owned mapping declarations explicit.

## What Changes

- Aggregate `MyBatisStatementAuthorization` beans into a default `MyBatisStatementRegistry`.
- Aggregate `MyBatisResourceMapping` beans into the translator mapping instead of requiring a raw
  `Map` bean.
- Provide an empty default `AuthorizationAttributesProvider` when the host does not need request
  attributes.
- Back off for host-provided registry, attributes provider, and interceptor beans.
- Activate MyBatis auto-configuration only when MyBatis is on the application classpath.
- Reject duplicate resource mappings during startup with a clear configuration error.
- Keep exactly one `AuthenticatedSubjectProvider` as an explicit host/runtime requirement.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `mybatis-spring-auto-integration`: Define complete starter assembly, optional-classpath behavior,
  default request attributes, host overrides, and duplicate mapping validation.

## Impact

The change affects `forga-spring-boot-starter` MyBatis auto-configuration and its context tests. It
does not change authorization semantics, relationship ownership, or the framework-neutral
`forga-mybatis` API.
