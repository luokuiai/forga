package com.luokuiai.forga.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.eval.AuthorizationEvaluator;
import com.luokuiai.forga.core.eval.DecisionReason;
import com.luokuiai.forga.core.eval.EvaluationLimits;
import com.luokuiai.forga.core.eval.RelationLookupRequest;
import com.luokuiai.forga.core.eval.RelationshipEntry;
import com.luokuiai.forga.core.eval.RelationshipLookup;
import com.luokuiai.forga.core.eval.RelationshipLookupException;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.PermissionRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.core.policy.PermissionExpression;
import com.luokuiai.forga.core.policy.PolicyCompiler;
import com.luokuiai.forga.core.policy.PolicyDefinition;
import com.luokuiai.forga.core.policy.ResolverCapabilities;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScopedAuthorizationServiceTest {

  private static final SubjectRef ALICE = new SubjectRef("principal", "alice");

  private static final ScopeRef ALPHA = new ScopeRef("workspace", "alpha");

  private static final ScopeRef BETA = new ScopeRef("workspace", "beta");

  private static final ObjectRef REPORT = new ObjectRef("report", "report-1");

  private static final PermissionRef VIEW = new PermissionRef("view");

  private static final RelationRef VIEWER = new RelationRef("viewer");

  @Test
  void allowsScopeSwitchThroughMembershipTemplate() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            evaluator(ScopePolicyTemplates.enterScopePolicy(), lookup),
            object -> Optional.empty());

    ScopeSwitchDecision decision =
        service.canSwitch(
            new ScopeSwitchRequest(ALICE, ALPHA, ScopePolicyTemplates.ENTER));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.activeScope()).contains(new ActiveScope(ALPHA));
  }

  @Test
  void deniesScopeSwitchWithoutRequiredRelation() {
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            evaluator(ScopePolicyTemplates.enterScopePolicy(), new CountingLookup()),
            object -> Optional.empty());

    ScopeSwitchDecision decision =
        service.canSwitch(new ScopeSwitchRequest(ALICE, BETA, ScopePolicyTemplates.ENTER));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.activeScope()).isEmpty();
  }

  @Test
  void failClosesWhenSwitchResolverFails() {
    RelationshipLookup lookup =
        requests -> {
          throw new RelationshipLookupException(DecisionReason.RESOLVER_FAILURE, "down");
        };
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            evaluator(ScopePolicyTemplates.enterScopePolicy(), lookup),
            object -> Optional.empty());

    ScopeSwitchDecision decision =
        service.canSwitch(new ScopeSwitchRequest(ALICE, ALPHA, ScopePolicyTemplates.ENTER));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE);
  }

  @Test
  void allowsScopedPermissionWhenActiveScopeMatchesRelationshipGraph() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(REPORT, VIEWER), RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            scopedPermissionEvaluator(lookup), object -> Optional.of(ALPHA));

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(ALPHA))));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.ALLOWED);
  }

  @Test
  void allowsStrictPermissionWhenObjectBelongsToActiveScope() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(REPORT, VIEWER), RelationshipEntry.subject(ALICE));
    boolean[] crossScopeCalled = {false};
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            scopedPermissionEvaluator(lookup),
            object -> Optional.of(ALPHA),
            request -> {
              crossScopeCalled[0] = true;
              return false;
            });

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(ALPHA))));

    assertThat(decision.allowed()).isTrue();
    assertThat(crossScopeCalled[0]).isFalse();
    assertThat(lookup.calls()).isEqualTo(3);
  }

  @Test
  void deniesStrictPermissionWhenObjectBelongsToAnotherScope() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(REPORT, VIEWER), RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        ScopedAuthorizationService.strict(
            scopedPermissionEvaluator(lookup), object -> Optional.of(BETA));

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(ALPHA))));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.NO_MATCH);
    assertThat(lookup.calls()).isEqualTo(2);
  }

  @Test
  void allowsStrictCrossScopePermissionThroughExplicitGrant() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(REPORT, VIEWER), RelationshipEntry.subject(ALICE));
    CrossScopeAccessRequest[] resolvedRequest = {null};
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            scopedPermissionEvaluator(lookup),
            object -> Optional.of(BETA),
            request -> {
              resolvedRequest[0] = request;
              return true;
            });

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(ALPHA))));

    assertThat(decision.allowed()).isTrue();
    assertThat(resolvedRequest[0].activeScope()).isEqualTo(ALPHA);
    assertThat(resolvedRequest[0].objectScope()).isEqualTo(BETA);
    assertThat(resolvedRequest[0].object()).isEqualTo(REPORT);
    assertThat(resolvedRequest[0].permission()).isEqualTo(VIEW);
    assertThat(resolvedRequest[0].subject()).isEqualTo(ALICE);
    assertThat(lookup.calls()).isEqualTo(3);
  }

  @Test
  void deniesCrossScopePermissionWhenGrantAllowsButObjectPermissionDoesNot() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            scopedPermissionEvaluator(lookup),
            object -> Optional.of(BETA),
            request -> true);

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(ALPHA))));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.NO_MATCH);
    assertThat(lookup.calls()).isEqualTo(3);
  }

  @Test
  void failClosesWhenStrictObjectScopeIsMissing() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(REPORT, VIEWER), RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        ScopedAuthorizationService.strict(
            scopedPermissionEvaluator(lookup), object -> Optional.empty());

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(ALPHA))));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.NO_MATCH);
    assertThat(lookup.calls()).isEqualTo(2);
  }

  @Test
  void failClosesWhenStrictObjectScopeResolverReturnsNull() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        ScopedAuthorizationService.strict(
            scopedPermissionEvaluator(lookup), object -> null);

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(ALPHA))));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE);
    assertThat(lookup.calls()).isEqualTo(2);
  }

  @Test
  void failClosesWhenStrictObjectScopeResolverFails() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        ScopedAuthorizationService.strict(
            scopedPermissionEvaluator(lookup),
            object -> {
              throw new IllegalStateException("down");
            });

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(ALPHA))));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE);
    assertThat(lookup.calls()).isEqualTo(2);
  }

  @Test
  void failClosesWhenCrossScopeAccessResolverFails() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            scopedPermissionEvaluator(lookup),
            object -> Optional.of(BETA),
            request -> {
              throw new IllegalStateException("down");
            });

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(ALPHA))));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE);
    assertThat(lookup.calls()).isEqualTo(2);
  }

  @Test
  void strictPermissionStopsBeforeBoundaryResolutionWithoutActiveScope() {
    CountingLookup lookup = new CountingLookup();
    boolean[] objectScopeCalled = {false};
    ScopedAuthorizationService service =
        ScopedAuthorizationService.strict(
            scopedPermissionEvaluator(lookup),
            object -> {
              objectScopeCalled[0] = true;
              return Optional.of(ALPHA);
            });

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(REPORT, VIEW, ScopedSubject.withoutScope(ALICE)));

    assertThat(decision.allowed()).isFalse();
    assertThat(objectScopeCalled[0]).isFalse();
    assertThat(lookup.calls()).isZero();
  }

  @Test
  void strictPermissionStopsBeforeBoundaryResolutionWhenScopeEntryIsDenied() {
    CountingLookup lookup = new CountingLookup();
    boolean[] objectScopeCalled = {false};
    ScopedAuthorizationService service =
        ScopedAuthorizationService.strict(
            scopedPermissionEvaluator(lookup),
            object -> {
              objectScopeCalled[0] = true;
              return Optional.of(BETA);
            });

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(BETA))));

    assertThat(decision.allowed()).isFalse();
    assertThat(objectScopeCalled[0]).isFalse();
  }

  @Test
  void deniesScopedPermissionWithoutActiveScope() {
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            scopedPermissionEvaluator(new CountingLookup()), object -> Optional.empty());

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(REPORT, VIEW, ScopedSubject.withoutScope(ALICE)));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.NO_MATCH);
  }

  @Test
  void deniesScopedPermissionWhenSubjectCannotEnterActiveScope() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.MEMBER),
        RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(REPORT, VIEWER), RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            scopedPermissionEvaluator(lookup), object -> Optional.of(ALPHA));

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(
                REPORT, VIEW, ScopedSubject.of(ALICE, new ActiveScope(BETA))));

    assertThat(decision.allowed()).isFalse();
  }

  @Test
  void policyTemplatesCanGrantAssignedAccessAndApplyDenialOverride() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(ALPHA.toObjectRef(), ScopePolicyTemplates.ASSIGNED),
        RelationshipEntry.subject(ALICE));
    lookup.put(
        new RelationLookupRequest(BETA.toObjectRef(), ScopePolicyTemplates.ASSIGNED),
        RelationshipEntry.subject(ALICE));
    lookup.put(
        new RelationLookupRequest(BETA.toObjectRef(), ScopePolicyTemplates.DENIED),
        RelationshipEntry.subject(ALICE));
    ScopedAuthorizationService service =
        new ScopedAuthorizationService(
            evaluator(ScopePolicyTemplates.enterScopePolicy(), lookup),
            object -> Optional.empty());

    ScopeSwitchDecision allowed =
        service.canSwitch(new ScopeSwitchRequest(ALICE, ALPHA, ScopePolicyTemplates.ENTER));
    ScopeSwitchDecision denied =
        service.canSwitch(new ScopeSwitchRequest(ALICE, BETA, ScopePolicyTemplates.ENTER));

    assertThat(allowed.allowed()).isTrue();
    assertThat(denied.allowed()).isFalse();
  }

  private static AuthorizationEvaluator evaluator(
      PolicyDefinition policy, RelationshipLookup lookup) {
    return evaluator(
        PolicyCompiler.compile(
            policy,
            ResolverCapabilities.of(
                List.of(
                    ScopePolicyTemplates.MEMBER,
                    ScopePolicyTemplates.ASSIGNED,
                    ScopePolicyTemplates.DENIED),
                List.of())),
        lookup);
  }

  private static AuthorizationEvaluator evaluator(
      CompiledPolicy policy, RelationshipLookup lookup) {
    return new AuthorizationEvaluator(policy, lookup, EvaluationLimits.defaults());
  }

  private static AuthorizationEvaluator scopedPermissionEvaluator(RelationshipLookup lookup) {
    return evaluator(
        PolicyCompiler.compile(
            new PolicyDefinition(
                Map.of(
                    VIEW,
                    PermissionExpression.relation(VIEWER),
                    ScopePolicyTemplates.ENTER,
                    ScopePolicyTemplates.enterScope())),
            ResolverCapabilities.of(
                List.of(
                    VIEWER,
                    ScopePolicyTemplates.MEMBER,
                    ScopePolicyTemplates.ASSIGNED,
                    ScopePolicyTemplates.DENIED),
                List.of())),
        lookup);
  }

  private static final class CountingLookup implements RelationshipLookup {

    private final Map<RelationLookupRequest, List<RelationshipEntry>> entries = new HashMap<>();

    private int calls;

    void put(RelationLookupRequest request, RelationshipEntry entry) {
      entries.put(request, List.of(entry));
    }

    int calls() {
      return calls;
    }

    @Override
    public Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
        List<RelationLookupRequest> requests) {
      calls++;
      Map<RelationLookupRequest, List<RelationshipEntry>> result = new HashMap<>();
      requests.forEach(request -> result.put(request, entries.getOrDefault(request, List.of())));
      return result;
    }
  }
}
