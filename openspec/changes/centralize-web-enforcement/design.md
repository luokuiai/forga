## Context

See proposal.md. MVC metadata compilation already validates exact Contributor registrations, but interceptor registration is conditional on an authorizer and not scoped.

## Goals / Non-Goals

Goals: one Starter-owned enforcement path, explicit startup failure for incomplete setup, and host-owned path policy. Non-goals: authentication-framework permission decisions or business-data ownership.

## Decisions

- Keep Spring MVC `HandlerInterceptor` as the request hook. It has resolved `HandlerMethod` metadata; a servlet filter would need to repeat MVC handler selection. Move the implementation into the Starter as a package-private class and delete the public Web-module interceptor.
- Use a single immutable `ForgaWebScope` bean for include/exclude patterns, with default `/**`. The Starter owns `WebMvcConfigurer` registration, and `@EnableForga` remains the only activation switch.
- Validate scope against Spring MVC's mapped handlers at startup after registrations compile. Reuse Spring path-pattern matching semantics; reject static declarations outside the scope. Dynamic host resolvers cannot be statically classified and still fail closed at request time.
- Make optional adapter auto-configurations conditional on the Starter's enablement-validation bean, ordered after its auto-configuration. This follows `@EnableForga` without making adapter modules depend on the Starter or moving a Spring annotation into domain-neutral core.
- No collection API or graph traversal changes; existing resolver limits and batching are unaffected.

## Risks / Trade-offs

- Pattern coverage over URI templates can be subtle → test literal, template, exclusion, and multi-path mappings; report handler and path in startup error.
- Removing the public interceptor is source-incompatible → update README and examples before first release.
- Hosts with MVC but no endpoint permissions now need an authorizer → fail early with actionable error.

## Migration Plan

Delete manual interceptor registration, provide `EndpointPermissionAuthorizer`, and optionally provide a `ForgaWebScope` bean. Rollback is reverting this pre-release change.
