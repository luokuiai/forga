package com.luokuiai.forga.scope;

import com.luokuiai.forga.core.eval.AuthorizationEvaluator;
import com.luokuiai.forga.core.eval.CheckDecision;
import com.luokuiai.forga.core.eval.CheckRequest;
import com.luokuiai.forga.core.eval.DecisionReason;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.PermissionRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Service facade for scope switching and active-scope permission checks. */
public final class ScopedAuthorizationService {

  private final AuthorizationEvaluator evaluator;

  private final PermissionRef scopeEntryPermission;

  private final ObjectScopeLookup objectScopes;

  private final CrossScopeGrantLookup crossScopeGrants;

  /**
   * Creates a strict service that denies every cross-scope request.
   *
   * @param evaluator evaluator used for underlying authorization checks
   * @param objectScopes host lookup for object ownership
   * @return strict scoped authorization service
   */
  public static ScopedAuthorizationService strict(
      AuthorizationEvaluator evaluator, ObjectScopeLookup objectScopes) {
    return new ScopedAuthorizationService(
        evaluator, ScopePolicyTemplates.ENTER, objectScopes, CrossScopeGrantLookup.denyAll());
  }

  /**
   * Creates a service using the default scope-entry permission and denying cross-scope requests.
   *
   * @param evaluator evaluator used for underlying authorization checks
   * @param objectScopes host lookup for object ownership
   */
  public ScopedAuthorizationService(
      AuthorizationEvaluator evaluator, ObjectScopeLookup objectScopes) {
    this(evaluator, ScopePolicyTemplates.ENTER, objectScopes, CrossScopeGrantLookup.denyAll());
  }

  /**
   * Creates a service using the default scope-entry permission.
   *
   * @param evaluator evaluator used for underlying authorization checks
   * @param objectScopes host lookup for object ownership
   * @param crossScopeGrants host lookup for explicit cross-scope grants
   */
  public ScopedAuthorizationService(
      AuthorizationEvaluator evaluator,
      ObjectScopeLookup objectScopes,
      CrossScopeGrantLookup crossScopeGrants) {
    this(evaluator, ScopePolicyTemplates.ENTER, objectScopes, crossScopeGrants);
  }

  /**
   * Creates a scoped authorization service.
   *
   * @param evaluator evaluator used for underlying authorization checks
   * @param scopeEntryPermission permission required on a selected scope
   * @param objectScopes host lookup for object ownership
   * @param crossScopeGrants host lookup for explicit cross-scope grants
   */
  public ScopedAuthorizationService(
      AuthorizationEvaluator evaluator,
      PermissionRef scopeEntryPermission,
      ObjectScopeLookup objectScopes,
      CrossScopeGrantLookup crossScopeGrants) {
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
    this.scopeEntryPermission =
        Objects.requireNonNull(scopeEntryPermission, "scope entry permission is required");
    this.objectScopes = Objects.requireNonNull(objectScopes, "object scopes are required");
    this.crossScopeGrants =
        Objects.requireNonNull(crossScopeGrants, "cross-scope grants are required");
  }

  /**
   * Checks whether a subject can enter a target scope.
   *
   * @param request switch request
   * @return switch decision
   */
  public ScopeSwitchDecision canSwitch(ScopeSwitchRequest request) {
    Objects.requireNonNull(request, "request is required");
    CheckDecision decision =
        evaluator.check(
            new CheckRequest(
                request.targetScope().toObjectRef(),
                scopeEntryPermission,
                request.subject(),
                request.attributes()));
    Optional<ActiveScope> activeScope =
        decision.allowed() ? Optional.of(new ActiveScope(request.targetScope())) : Optional.empty();
    return new ScopeSwitchDecision(request, decision, activeScope);
  }

  /**
   * Checks one permission requiring an active scope.
   *
   * @param request scoped permission request
   * @return scoped permission decision
   */
  public ScopedPermissionDecision check(ScopedPermissionRequest request) {
    Objects.requireNonNull(request, "request is required");
    return bulkCheck(List.of(request)).get(0);
  }

  /**
   * Checks a bounded batch of permissions requiring active scopes.
   *
   * <p>Boundary lookups are issued once per stage. Implementations should execute this composed
   * call inside one host transaction or equivalent request snapshot when the backing stores can
   * change concurrently.
   *
   * @param requests scoped permission requests
   * @return decisions in request order
   */
  public List<ScopedPermissionDecision> bulkCheck(List<ScopedPermissionRequest> requests) {
    List<ScopedPermissionRequest> immutableRequests = List.copyOf(requests);
    if (immutableRequests.isEmpty()) {
      return List.of();
    }

    Map<ScopedPermissionRequest, ScopedPermissionDecision> decisions = new LinkedHashMap<>();
    List<ScopedPermissionRequest> withActiveScope = new ArrayList<>();
    for (ScopedPermissionRequest request : immutableRequests) {
      if (request.activeScope().isEmpty()) {
        decisions.put(
            request,
            denied(request, ScopeAuthorizationPhase.ACTIVE_SCOPE, DecisionReason.NO_MATCH));
      } else {
        withActiveScope.add(request);
      }
    }

    List<ScopedPermissionRequest> entered = evaluateScopeEntry(withActiveScope, decisions);
    if (entered.isEmpty()) {
      return ordered(immutableRequests, decisions);
    }

    Map<ObjectRef, Optional<ScopeRef>> ownership;
    List<ObjectRef> objects =
        entered.stream().map(ScopedPermissionRequest::object).distinct().toList();
    try {
      ownership = objectScopes.resolve(objects);
    } catch (RuntimeException exception) {
      failAll(
          entered,
          decisions,
          ScopeAuthorizationPhase.OBJECT_SCOPE,
          DecisionReason.RESOLVER_FAILURE);
      return ordered(immutableRequests, decisions);
    }
    if (!complete(objects, ownership)) {
      failAll(
          entered,
          decisions,
          ScopeAuthorizationPhase.OBJECT_SCOPE,
          DecisionReason.RESOLVER_FAILURE);
      return ordered(immutableRequests, decisions);
    }

    List<ScopedPermissionRequest> boundaryAccepted = new ArrayList<>();
    Map<ScopedPermissionRequest, CrossScopeAccessRequest> crossScopeRequests =
        new LinkedHashMap<>();
    for (ScopedPermissionRequest request : entered) {
      Optional<ScopeRef> objectScope = ownership.get(request.object());
      if (objectScope.isEmpty()) {
        decisions.put(
            request,
            denied(request, ScopeAuthorizationPhase.OBJECT_SCOPE, DecisionReason.NO_MATCH));
        continue;
      }
      ScopeRef activeScope = request.activeScope().orElseThrow().scope();
      if (activeScope.equals(objectScope.orElseThrow())) {
        boundaryAccepted.add(request);
      } else {
        crossScopeRequests.put(
            request,
            new CrossScopeAccessRequest(
                activeScope,
                objectScope.orElseThrow(),
                request.object(),
                request.permission(),
                request.subject(),
                request.attributes()));
      }
    }

    resolveCrossScope(crossScopeRequests, boundaryAccepted, decisions);
    evaluateObjectPermissions(boundaryAccepted, decisions);
    return ordered(immutableRequests, decisions);
  }

  private List<ScopedPermissionRequest> evaluateScopeEntry(
      List<ScopedPermissionRequest> requests,
      Map<ScopedPermissionRequest, ScopedPermissionDecision> decisions) {
    List<CheckRequest> checks =
        requests.stream()
            .map(
                request ->
                    new CheckRequest(
                        request.activeScope().orElseThrow().scope().toObjectRef(),
                        scopeEntryPermission,
                        request.subject(),
                        request.attributes()))
            .toList();
    List<CheckDecision> results = evaluator.bulkCheck(checks);
    List<ScopedPermissionRequest> entered = new ArrayList<>();
    for (int index = 0; index < requests.size(); index++) {
      ScopedPermissionRequest request = requests.get(index);
      CheckDecision result = results.get(index);
      if (result.allowed()) {
        entered.add(request);
      } else {
        decisions.put(
            request,
            denied(request, ScopeAuthorizationPhase.SCOPE_ENTRY, result.reason()));
      }
    }
    return entered;
  }

  private void resolveCrossScope(
      Map<ScopedPermissionRequest, CrossScopeAccessRequest> requests,
      List<ScopedPermissionRequest> accepted,
      Map<ScopedPermissionRequest, ScopedPermissionDecision> decisions) {
    if (requests.isEmpty()) {
      return;
    }
    List<CrossScopeAccessRequest> lookups = requests.values().stream().distinct().toList();
    Map<CrossScopeAccessRequest, Boolean> grants;
    try {
      grants = crossScopeGrants.resolve(lookups);
    } catch (RuntimeException exception) {
      failAll(
          requests.keySet(),
          decisions,
          ScopeAuthorizationPhase.CROSS_SCOPE_GRANT,
          DecisionReason.RESOLVER_FAILURE);
      return;
    }
    if (!complete(lookups, grants)) {
      failAll(
          requests.keySet(),
          decisions,
          ScopeAuthorizationPhase.CROSS_SCOPE_GRANT,
          DecisionReason.RESOLVER_FAILURE);
      return;
    }
    requests.forEach(
        (request, lookup) -> {
          if (grants.get(lookup)) {
            accepted.add(request);
          } else {
            decisions.put(
                request,
                denied(
                    request,
                    ScopeAuthorizationPhase.CROSS_SCOPE_GRANT,
                    DecisionReason.NO_MATCH));
          }
        });
  }

  private void evaluateObjectPermissions(
      List<ScopedPermissionRequest> requests,
      Map<ScopedPermissionRequest, ScopedPermissionDecision> decisions) {
    List<CheckDecision> results =
        evaluator.bulkCheck(
            requests.stream().map(ScopedAuthorizationService::objectCheck).toList());
    for (int index = 0; index < requests.size(); index++) {
      ScopedPermissionRequest request = requests.get(index);
      decisions.put(
          request,
          new ScopedPermissionDecision(
              request, ScopeAuthorizationPhase.OBJECT_PERMISSION, results.get(index)));
    }
  }

  private static <K, V> boolean complete(List<K> requests, Map<K, V> resolved) {
    return resolved != null
        && resolved.size() == Set.copyOf(requests).size()
        && resolved.keySet().equals(Set.copyOf(requests))
        && resolved.values().stream().allMatch(Objects::nonNull);
  }

  private static void failAll(
      Iterable<ScopedPermissionRequest> requests,
      Map<ScopedPermissionRequest, ScopedPermissionDecision> decisions,
      ScopeAuthorizationPhase phase,
      DecisionReason reason) {
    requests.forEach(request -> decisions.put(request, denied(request, phase, reason)));
  }

  private static List<ScopedPermissionDecision> ordered(
      List<ScopedPermissionRequest> requests,
      Map<ScopedPermissionRequest, ScopedPermissionDecision> decisions) {
    return requests.stream().map(decisions::get).toList();
  }

  private static CheckRequest objectCheck(ScopedPermissionRequest request) {
    return new CheckRequest(
        request.object(), request.permission(), request.subject(), request.attributes());
  }

  private static ScopedPermissionDecision denied(
      ScopedPermissionRequest request, ScopeAuthorizationPhase phase, DecisionReason reason) {
    return new ScopedPermissionDecision(
        request, phase, new CheckDecision(objectCheck(request), false, reason));
  }
}
