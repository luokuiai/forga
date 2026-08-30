package com.luokuiai.forga.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.eval.AuthorizationEvaluator;
import com.luokuiai.forga.core.eval.BatchResolution;
import com.luokuiai.forga.core.eval.DecisionReason;
import com.luokuiai.forga.core.eval.EvaluationLimits;
import com.luokuiai.forga.core.eval.EvaluationReadContext;
import com.luokuiai.forga.core.eval.RelationLookupRequest;
import com.luokuiai.forga.core.eval.RelationshipEntry;
import com.luokuiai.forga.core.eval.RelationshipLookup;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.PermissionRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.policy.PermissionExpression;
import com.luokuiai.forga.core.policy.PolicyCompiler;
import com.luokuiai.forga.core.policy.PolicyDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ScopedAuthorizationServiceTest {

  private static final SubjectRef ALICE = new SubjectRef("principal", "alice");

  private static final ScopeRef ALPHA = new ScopeRef("organization", "alpha");

  private static final ScopeRef BETA = new ScopeRef("organization", "beta");

  private static final ObjectRef REPORT_ONE = new ObjectRef("report", "one");

  private static final ObjectRef REPORT_TWO = new ObjectRef("report", "two");

  private static final PermissionRef VIEW = new PermissionRef("view");

  private static final PermissionRef ENTER = new PermissionRef("use-position");

  private static final RelationRef VIEWER = new RelationRef("viewer");

  private static final RelationRef ASSIGNEE = new RelationRef("position-assignee");

  @Test
  void switchUsesConfiguredEntryPermission() {
    CountingRelationshipLookup relationships = new CountingRelationshipLookup();
    relationships.put(ALPHA.toObjectRef(), ASSIGNEE, ALICE);
    ScopedAuthorizationService service =
        service(relationships, scopes(Map.of()), CrossScopeGrantLookup.denyAll());

    ScopeSwitchDecision decision = service.canSwitch(new ScopeSwitchRequest(ALICE, ALPHA));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.decision().request().permission()).isEqualTo(ENTER);
  }

  @Test
  void bulkCheckBatchesOwnershipCrossScopeAndCoreChecks() {
    CountingRelationshipLookup relationships = allowedRelationships();
    AtomicInteger ownershipCalls = new AtomicInteger();
    AtomicInteger grantCalls = new AtomicInteger();
    ObjectScopeLookup ownership =
        objects -> {
          ownershipCalls.incrementAndGet();
          assertThat(objects).containsExactlyInAnyOrder(REPORT_ONE, REPORT_TWO);
          return Map.of(REPORT_ONE, Optional.of(ALPHA), REPORT_TWO, Optional.of(BETA));
        };
    CrossScopeGrantLookup grants =
        requests -> {
          grantCalls.incrementAndGet();
          assertThat(requests).hasSize(1);
          return Map.of(requests.get(0), true);
        };
    ScopedAuthorizationService service = service(relationships, ownership, grants);

    List<ScopedPermissionDecision> decisions =
        service.bulkCheck(List.of(request(REPORT_ONE, ALPHA), request(REPORT_TWO, ALPHA)));

    assertThat(decisions).allMatch(ScopedPermissionDecision::allowed);
    assertThat(decisions)
        .extracting(ScopedPermissionDecision::phase)
        .containsOnly(ScopeAuthorizationPhase.OBJECT_PERMISSION);
    assertThat(ownershipCalls).hasValue(1);
    assertThat(grantCalls).hasValue(1);
  }

  @Test
  void missingActiveScopeStopsBeforeHostLookups() {
    AtomicInteger ownershipCalls = new AtomicInteger();
    ScopedAuthorizationService service =
        service(
            new CountingRelationshipLookup(),
            objects -> {
              ownershipCalls.incrementAndGet();
              return Map.of();
            },
            CrossScopeGrantLookup.denyAll());

    ScopedPermissionDecision decision =
        service.check(
            new ScopedPermissionRequest(REPORT_ONE, VIEW, ScopedSubject.withoutScope(ALICE)));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.phase()).isEqualTo(ScopeAuthorizationPhase.ACTIVE_SCOPE);
    assertThat(ownershipCalls).hasValue(0);
  }

  @Test
  void deniedEntryStopsBeforeOwnershipLookup() {
    AtomicInteger ownershipCalls = new AtomicInteger();
    ScopedAuthorizationService service =
        service(
            new CountingRelationshipLookup(),
            objects -> {
              ownershipCalls.incrementAndGet();
              return Map.of();
            },
            CrossScopeGrantLookup.denyAll());

    ScopedPermissionDecision decision = service.check(request(REPORT_ONE, ALPHA));

    assertThat(decision.phase()).isEqualTo(ScopeAuthorizationPhase.SCOPE_ENTRY);
    assertThat(ownershipCalls).hasValue(0);
  }

  @Test
  void incompleteOwnershipBatchFailsClosed() {
    ScopedAuthorizationService service =
        service(allowedRelationships(), objects -> Map.of(), CrossScopeGrantLookup.denyAll());

    ScopedPermissionDecision decision = service.check(request(REPORT_ONE, ALPHA));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.phase()).isEqualTo(ScopeAuthorizationPhase.OBJECT_SCOPE);
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE);
  }

  @Test
  void unresolvedOwnershipIsANormalDenial() {
    ScopedAuthorizationService service =
        service(
            allowedRelationships(),
            scopes(Map.of(REPORT_ONE, Optional.empty())),
            CrossScopeGrantLookup.denyAll());

    ScopedPermissionDecision decision = service.check(request(REPORT_ONE, ALPHA));

    assertThat(decision.phase()).isEqualTo(ScopeAuthorizationPhase.OBJECT_SCOPE);
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.NO_MATCH);
  }

  @Test
  void deniedCrossScopeGrantIdentifiesBoundaryPhase() {
    ScopedAuthorizationService service =
        service(
            allowedRelationships(),
            scopes(Map.of(REPORT_ONE, Optional.of(BETA))),
            CrossScopeGrantLookup.denyAll());

    ScopedPermissionDecision decision = service.check(request(REPORT_ONE, ALPHA));

    assertThat(decision.phase()).isEqualTo(ScopeAuthorizationPhase.CROSS_SCOPE_GRANT);
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.NO_MATCH);
  }

  @Test
  void incompleteCrossScopeBatchFailsClosed() {
    ScopedAuthorizationService service =
        service(
            allowedRelationships(),
            scopes(Map.of(REPORT_ONE, Optional.of(BETA))),
            requests -> Map.of());

    ScopedPermissionDecision decision = service.check(request(REPORT_ONE, ALPHA));

    assertThat(decision.phase()).isEqualTo(ScopeAuthorizationPhase.CROSS_SCOPE_GRANT);
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE);
  }

  @Test
  void ordinaryObjectDenialIdentifiesPermissionPhase() {
    CountingRelationshipLookup relationships = new CountingRelationshipLookup();
    relationships.put(ALPHA.toObjectRef(), ASSIGNEE, ALICE);
    ScopedAuthorizationService service =
        service(
            relationships,
            scopes(Map.of(REPORT_ONE, Optional.of(ALPHA))),
            CrossScopeGrantLookup.denyAll());

    ScopedPermissionDecision decision = service.check(request(REPORT_ONE, ALPHA));

    assertThat(decision.phase()).isEqualTo(ScopeAuthorizationPhase.OBJECT_PERMISSION);
    assertThat(decision.decision().reason()).isEqualTo(DecisionReason.NO_MATCH);
  }

  private static ScopedPermissionRequest request(ObjectRef object, ScopeRef activeScope) {
    return new ScopedPermissionRequest(
        object, VIEW, ScopedSubject.of(ALICE, new ActiveScope(activeScope)));
  }

  private static ObjectScopeLookup scopes(Map<ObjectRef, Optional<ScopeRef>> scopes) {
    return objects ->
        objects.stream()
            .distinct()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(object -> object, scopes::get));
  }

  private static CountingRelationshipLookup allowedRelationships() {
    CountingRelationshipLookup relationships = new CountingRelationshipLookup();
    relationships.put(ALPHA.toObjectRef(), ASSIGNEE, ALICE);
    relationships.put(REPORT_ONE, VIEWER, ALICE);
    relationships.put(REPORT_TWO, VIEWER, ALICE);
    return relationships;
  }

  private static ScopedAuthorizationService service(
      RelationshipLookup relationships,
      ObjectScopeLookup objectScopes,
      CrossScopeGrantLookup crossScopeGrants) {
    PolicyDefinition policy =
        new PolicyDefinition(
            Map.of(
                ENTER,
                PermissionExpression.relation(ASSIGNEE),
                VIEW,
                PermissionExpression.relation(VIEWER)));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            PolicyCompiler.compile(policy), relationships, EvaluationLimits.defaults());
    return new ScopedAuthorizationService(evaluator, ENTER, objectScopes, crossScopeGrants);
  }

  private static final class CountingRelationshipLookup implements RelationshipLookup {

    private final Map<RelationLookupRequest, List<RelationshipEntry>> entries = new HashMap<>();

    void put(ObjectRef object, RelationRef relation, SubjectRef subject) {
      entries.put(
          new RelationLookupRequest(object, relation),
          List.of(RelationshipEntry.subject(subject)));
    }

    @Override
    public BatchResolution<RelationLookupRequest, List<RelationshipEntry>> resolve(
        List<RelationLookupRequest> requests, EvaluationReadContext context) {
      Map<RelationLookupRequest, List<RelationshipEntry>> result = new HashMap<>();
      requests.forEach(request -> result.put(request, entries.getOrDefault(request, List.of())));
      return BatchResolution.unversioned(result);
    }
  }
}
