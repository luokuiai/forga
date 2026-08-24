package com.luokuiai.forga.scope;

import com.luokuiai.forga.core.eval.AuthorizationEvaluator;
import com.luokuiai.forga.core.eval.CheckDecision;
import com.luokuiai.forga.core.eval.CheckRequest;
import com.luokuiai.forga.core.eval.DecisionReason;
import com.luokuiai.forga.core.model.PermissionRef;
import java.util.Objects;
import java.util.Optional;

/**
 * Service facade for scope switch and active-scope permission checks.
 */
public final class ScopedAuthorizationService {

  private final AuthorizationEvaluator evaluator;

  private final PermissionRef scopeEntryPermission;

  private final ObjectScopeResolver objectScopeResolver;

  private final CrossScopeAccessResolver crossScopeAccessResolver;

  /**
   * Creates a strict scoped authorization service that denies cross-scope access.
   *
   * @param evaluator evaluator used for underlying authorization checks
   * @param objectScopeResolver host resolver for object ownership
   * @return strict scoped authorization service
   */
  public static ScopedAuthorizationService strict(
      AuthorizationEvaluator evaluator, ObjectScopeResolver objectScopeResolver) {
    return new ScopedAuthorizationService(
        evaluator,
        ScopePolicyTemplates.ENTER,
        objectScopeResolver,
        CrossScopeAccessResolver.denyAll());
  }

  /**
   * Creates a scoped authorization service that denies cross-scope access.
   *
   * @param evaluator evaluator used for underlying authorization checks
   * @param objectScopeResolver host resolver for object ownership
   */
  public ScopedAuthorizationService(
      AuthorizationEvaluator evaluator, ObjectScopeResolver objectScopeResolver) {
    this(
        evaluator,
        ScopePolicyTemplates.ENTER,
        objectScopeResolver,
        CrossScopeAccessResolver.denyAll());
  }

  /**
   * Creates a strict scoped authorization service using the default scope entry permission.
   *
   * @param evaluator evaluator used for underlying authorization checks
   * @param objectScopeResolver host resolver for object ownership
   * @param crossScopeAccessResolver host resolver for explicit cross-scope grants
   */
  public ScopedAuthorizationService(
      AuthorizationEvaluator evaluator,
      ObjectScopeResolver objectScopeResolver,
      CrossScopeAccessResolver crossScopeAccessResolver) {
    this(
        evaluator,
        ScopePolicyTemplates.ENTER,
        objectScopeResolver,
        crossScopeAccessResolver);
  }

  /**
   * Creates a strict scoped authorization service.
   *
   * @param evaluator evaluator used for underlying authorization checks
   * @param scopeEntryPermission permission required on the active scope before object checks
   * @param objectScopeResolver host resolver for object ownership
   * @param crossScopeAccessResolver host resolver for explicit cross-scope grants
   */
  public ScopedAuthorizationService(
      AuthorizationEvaluator evaluator,
      PermissionRef scopeEntryPermission,
      ObjectScopeResolver objectScopeResolver,
      CrossScopeAccessResolver crossScopeAccessResolver) {
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator is required");
    this.scopeEntryPermission =
        Objects.requireNonNull(scopeEntryPermission, "scope entry permission is required");
    this.objectScopeResolver =
        Objects.requireNonNull(objectScopeResolver, "object scope resolver is required");
    this.crossScopeAccessResolver =
        Objects.requireNonNull(
            crossScopeAccessResolver, "cross-scope access resolver is required");
  }

  /**
   * Checks whether the subject can enter the target scope.
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
                request.permission(),
                request.subject(),
                request.attributes()));
    Optional<ActiveScope> activeScope =
        decision.allowed() ? Optional.of(new ActiveScope(request.targetScope())) : Optional.empty();
    return new ScopeSwitchDecision(request, decision, activeScope);
  }

  /**
   * Checks a permission requiring an active scope.
   *
   * @param request scoped permission request
   * @return scoped permission decision
   */
  public ScopedPermissionDecision check(ScopedPermissionRequest request) {
    Objects.requireNonNull(request, "request is required");
    CheckRequest objectCheckRequest = objectCheckRequest(request);
    if (request.activeScope().isEmpty()) {
      return denied(request, objectCheckRequest, DecisionReason.NO_MATCH);
    }
    ActiveScope activeScope = request.activeScope().orElseThrow();
    CheckDecision scopeDecision =
        evaluator.check(
            new CheckRequest(
                activeScope.scope().toObjectRef(),
                scopeEntryPermission,
                request.subject(),
                request.attributes()));
    if (!scopeDecision.allowed()) {
      return new ScopedPermissionDecision(request, scopeDecision);
    }
    Optional<DecisionReason> boundaryFailure =
        boundaryFailure(request, activeScope.scope());
    if (boundaryFailure.isPresent()) {
      return denied(request, objectCheckRequest, boundaryFailure.orElseThrow());
    }
    return new ScopedPermissionDecision(request, evaluator.check(objectCheckRequest));
  }

  private Optional<DecisionReason> boundaryFailure(
      ScopedPermissionRequest request, ScopeRef activeScope) {
    Optional<ScopeRef> resolvedObjectScope;
    try {
      resolvedObjectScope = objectScopeResolver.resolve(request.object());
    } catch (RuntimeException exception) {
      return Optional.of(DecisionReason.RESOLVER_FAILURE);
    }
    if (resolvedObjectScope == null) {
      return Optional.of(DecisionReason.RESOLVER_FAILURE);
    }
    if (resolvedObjectScope.isEmpty()) {
      return Optional.of(DecisionReason.NO_MATCH);
    }
    ScopeRef objectScope = resolvedObjectScope.orElseThrow();
    if (activeScope.equals(objectScope)) {
      return Optional.empty();
    }
    CrossScopeAccessRequest crossScopeRequest =
        new CrossScopeAccessRequest(
            activeScope,
            objectScope,
            request.object(),
            request.permission(),
            request.subject(),
            request.attributes());
    try {
      return crossScopeAccessResolver.allows(crossScopeRequest)
          ? Optional.empty()
          : Optional.of(DecisionReason.NO_MATCH);
    } catch (RuntimeException exception) {
      return Optional.of(DecisionReason.RESOLVER_FAILURE);
    }
  }

  private static CheckRequest objectCheckRequest(ScopedPermissionRequest request) {
    return new CheckRequest(
        request.object(), request.permission(), request.subject(), request.attributes());
  }

  private static ScopedPermissionDecision denied(
      ScopedPermissionRequest request, CheckRequest checkRequest, DecisionReason reason) {
    return new ScopedPermissionDecision(
        request, new CheckDecision(checkRequest, false, reason));
  }
}
