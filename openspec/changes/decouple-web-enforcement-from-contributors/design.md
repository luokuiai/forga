## Context

See `proposal.md` for motivation. The Web auto-configuration currently has a class-level
contributor condition, so every bean in the common endpoint pipeline disappears when contributors
are absent. The composed resolver already supports annotations independently, and registration
assembly can represent an empty contributor collection.

## Goals / Non-Goals

**Goals:**

- Make annotation-only enforcement a first-class Starter configuration.
- Preserve optional external registrations and deterministic metadata composition.
- Preserve unresolved-handler failure and explicit permit-all semantics.
- Avoid activating enforcement when a host has not supplied an authorizer.

**Non-Goals:**

- Change resolver precedence, conflict detection, or authorization decisions.
- Add an enablement property or new public API.
- Make unannotated handlers implicitly public.

## Decisions

### Load common Web assembly without requiring contributors

Remove the class-level contributor condition and inject contributors through an object provider so
registration assembly naturally receives an empty ordered list. This keeps one composition path for
annotation-only and registry-based hosts instead of introducing a second resolver pipeline.

Alternative: synthesize an empty contributor bean. Rejected because it preserves the incorrect
coupling and exposes an implementation workaround to hosts.

### Use authorizer presence to activate automatic enforcement

Keep interceptor creation conditional on an endpoint authorizer. Do not back off for another
interceptor bean: the framework interceptor owns composition and fail-closed enforcement, while an
authorizer is the host capability it requires. Qualify Starter wiring to its own interceptor bean so
an unrelated host interceptor cannot make injection ambiguous.

Alternative: always install the interceptor whenever Forga is enabled. Rejected because hosts may
use non-Web Forga capabilities, and installing fail-closed MVC enforcement without an authorizer
would break unrelated Web handlers.

### Validate incomplete registry integrations only when contributors exist

Run startup validation only when at least one endpoint contributor exists. A contributor promises
registered endpoint enforcement and therefore requires an authorizer. A host interceptor is not a
substitute because Starter cannot prove that it uses composed metadata or fails closed. With no
contributors and no authorizer, common assembly may remain dormant without failing a non-Web
integration.

## Risks / Trade-offs

- [An authorizer bean now activates fail-closed checks for all MVC handlers] -> Document that every
  handler requires `@RequiresPermission`, `@PermitAll`, external registration, or host resolver
  metadata, and cover unresolved behavior in tests.
- [Empty registration assembly contributes an empty catalog source] -> Keep the existing immutable
  registration and catalog adapter; an empty contribution has no observable catalog entries.

## Migration Plan

Annotation-only hosts remove manual interceptor registration and rely on Starter assembly. Hosts
with contributors provide an authorizer and retain automatic enforcement. Snapshot hosts that
declared an interceptor bean migrate custom metadata into a resolver and custom decisions into an
authorizer. Rollback restores the contributor condition and manual annotation-only registration.
