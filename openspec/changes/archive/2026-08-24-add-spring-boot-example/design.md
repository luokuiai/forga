## Context

The starter intentionally requires host-owned policy, resolver, and identity inputs when
`@EnableForga` is present. Existing README sections describe these concepts separately, but users
cannot run or test the complete Spring assembly path from one reference application.

## Goals / Non-Goals

**Goals:**

- Provide a small application that starts through the same auto-configuration as a host service.
- Show the required beans and their ownership in ordinary Spring configuration.
- Exercise allowed, unauthenticated, and denied HTTP requests in automated tests.
- Keep the example in the root build while excluding it from Maven publication.

**Non-Goals:**

- Define a recommended host persistence schema or production authentication mechanism.
- Demonstrate every policy expression, resolver direction, or optional integration.
- Add convenience APIs solely for the example.

## Decisions

### Use one document-view permission

The policy defines `view_document = viewer`. An in-memory resolver grants `alice` the `viewer`
relation on `document:doc-1`. This is intentionally small while still exercising policy compilation,
resolver registration, evaluator assembly, and a real authorization decision.

### Use a request header as the example identity boundary

An `AuthenticatedSubjectProvider` maps `X-Subject-Id` to a neutral `SubjectRef`. The example labels
this as demonstration-only; production hosts should use the Sa-Token, Spring Security, or custom
authentication adapter appropriate to their environment.

### Keep endpoint mapping explicit

The controller maps its path variable to an `ObjectRef`, calls the assembled evaluator, and returns
401 for a missing subject or 403 for a denied decision. This makes the host-owned endpoint-to-object
mapping visible without hiding it behind another abstraction.

### Build but do not publish the example

The module participates in `clean check` and provides an application entry point. Root publication
configuration excludes example modules so Maven Central receives SDK artifacts only.

## Risks / Trade-offs

- [Users copy the header authentication into production] -> Name and document it as an example-only
  provider and point to supported authentication adapters.
- [The example becomes a second documentation source] -> Keep README guidance concise and link to
  the tested source files.
- [Root publication changes affect SDK modules] -> Preserve the existing publication block exactly
  for non-example projects and verify publish task topology.
