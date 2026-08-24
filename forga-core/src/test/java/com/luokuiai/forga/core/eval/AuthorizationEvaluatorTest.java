package com.luokuiai.forga.core.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.CaveatRef;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.PermissionRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.model.SubjectSetRef;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.core.policy.PermissionExpression;
import com.luokuiai.forga.core.policy.PolicyCompiler;
import com.luokuiai.forga.core.policy.PolicyDefinition;
import com.luokuiai.forga.core.policy.ResolverCapabilities;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class AuthorizationEvaluatorTest {

  private static final ObjectRef DOCUMENT = new ObjectRef("document", "doc-1");

  private static final SubjectRef ALICE = new SubjectRef("principal", "alice");

  private static final RelationRef VIEWER = new RelationRef("viewer");

  private static final RelationRef EDITOR = new RelationRef("editor");

  private static final RelationRef BLOCKED = new RelationRef("blocked");

  private static final RelationRef PARENT = new RelationRef("parent");

  private static final RelationRef MEMBER = new RelationRef("member");

  private static final PermissionRef VIEW = new PermissionRef("view");

  @Test
  void allowsDirectRelationMembership() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));

    CheckDecision decision = evaluator(relationPolicy(), lookup).check(request(VIEW));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.reason()).isEqualTo(DecisionReason.ALLOWED);
    assertThat(decision.proof()).containsExactly(new ProofStep(DOCUMENT, VIEWER, ALICE));
  }

  @Test
  void expandsSubjectSetMembership() {
    ObjectRef group = new ObjectRef("group", "engineering");
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(DOCUMENT, VIEWER),
        RelationshipEntry.subjectSet(new SubjectSetRef(group, new RelationRef("member"))));
    lookup.put(
        new RelationLookupRequest(group, new RelationRef("member")),
        RelationshipEntry.subject(ALICE));

    CheckDecision decision = evaluator(relationPolicy(), lookup).check(request(VIEW));

    assertThat(decision.allowed()).isTrue();
    assertThat(lookup.calls()).isEqualTo(2);
  }

  @Test
  void memoizesRepeatedRelationLookupsWithinRequest() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    CompiledPolicy policy =
        policy(
            PermissionExpression.union(
                List.of(
                    PermissionExpression.relation(VIEWER),
                    PermissionExpression.relation(VIEWER))));

    CheckDecision decision = evaluator(policy, lookup).check(request(VIEW));

    assertThat(decision.allowed()).isTrue();
    assertThat(lookup.calls()).isEqualTo(1);
  }

  @Test
  void bulkCheckSharesMemoizedRelationshipLookups() {
    SubjectRef bob = new SubjectRef("principal", "bob");
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(DOCUMENT, VIEWER),
        List.of(RelationshipEntry.subject(ALICE), RelationshipEntry.subject(bob)));
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), lookup);

    List<CheckDecision> decisions =
        evaluator.bulkCheck(
            List.of(
                new CheckRequest(DOCUMENT, VIEW, ALICE),
                new CheckRequest(DOCUMENT, VIEW, bob)));

    assertThat(decisions).extracting(CheckDecision::allowed).containsExactly(true, true);
    assertThat(lookup.calls()).isEqualTo(1);
  }

  @Test
  void bulkCheckBatchesDistinctObjectsAtOneFrontier() {
    ObjectRef secondDocument = new ObjectRef("document", "doc-2");
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    lookup.put(
        new RelationLookupRequest(secondDocument, VIEWER),
        RelationshipEntry.subject(ALICE));
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), lookup);

    List<CheckDecision> decisions =
        evaluator.bulkCheck(
            List.of(
                new CheckRequest(DOCUMENT, VIEW, ALICE),
                new CheckRequest(secondDocument, VIEW, ALICE)));

    assertThat(decisions).extracting(CheckDecision::allowed).containsExactly(true, true);
    assertThat(lookup.calls()).isEqualTo(1);
    assertThat(lookup.batchSizes()).containsExactly(2);
  }

  @Test
  void bulkCheckBatchesTraversalAtEachDiscoveredFrontier() {
    ObjectRef secondDocument = new ObjectRef("document", "doc-2");
    ObjectRef firstFolder = new ObjectRef("folder", "folder-1");
    ObjectRef secondFolder = new ObjectRef("folder", "folder-2");
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(DOCUMENT, PARENT),
        RelationshipEntry.subjectSet(new SubjectSetRef(firstFolder, MEMBER)));
    lookup.put(
        new RelationLookupRequest(secondDocument, PARENT),
        RelationshipEntry.subjectSet(new SubjectSetRef(secondFolder, MEMBER)));
    lookup.put(
        new RelationLookupRequest(firstFolder, MEMBER), RelationshipEntry.subject(ALICE));
    lookup.put(
        new RelationLookupRequest(secondFolder, MEMBER), RelationshipEntry.subject(ALICE));
    CompiledPolicy policy =
        policy(
            PermissionExpression.traversal(
                PARENT, "folder", PermissionExpression.relation(MEMBER)));

    List<CheckDecision> decisions =
        evaluator(policy, lookup)
            .bulkCheck(
                List.of(
                    new CheckRequest(DOCUMENT, VIEW, ALICE),
                    new CheckRequest(secondDocument, VIEW, ALICE)));

    assertThat(decisions).extracting(CheckDecision::allowed).containsExactly(true, true);
    assertThat(lookup.calls()).isEqualTo(2);
    assertThat(lookup.batchSizes()).containsExactly(2, 2);
  }

  @Test
  void bulkCheckDecisionsMatchIndividualChecksForDistinctObjects() {
    ObjectRef secondDocument = new ObjectRef("document", "doc-2");
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    List<CheckRequest> requests =
        List.of(
            new CheckRequest(DOCUMENT, VIEW, ALICE),
            new CheckRequest(secondDocument, VIEW, ALICE));
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), lookup);
    List<CheckDecision> individual = requests.stream().map(evaluator::check).toList();

    List<CheckDecision> bulk = evaluator(relationPolicy(), lookup).bulkCheck(requests);

    assertThat(bulk).isEqualTo(individual);
  }

  @Test
  void bulkCheckIsolatesVisitedNodeLimitsPerDecision() {
    ObjectRef secondDocument = new ObjectRef("document", "doc-2");
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    lookup.put(
        new RelationLookupRequest(secondDocument, VIEWER), RelationshipEntry.subject(ALICE));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            relationPolicy(),
            lookup,
            new EvaluationLimits(32, 1000, 1, 100, 10, Optional.empty()));

    List<CheckDecision> decisions =
        evaluator.bulkCheck(
            List.of(
                new CheckRequest(DOCUMENT, VIEW, ALICE),
                new CheckRequest(secondDocument, VIEW, ALICE)));

    assertThat(decisions).extracting(CheckDecision::allowed).containsExactly(true, true);
  }

  @Test
  void bulkCheckCountsLogicalLookupsForPrefetchedRelations() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(DOCUMENT, EDITOR), RelationshipEntry.subject(ALICE));
    CompiledPolicy policy =
        policy(
            PermissionExpression.intersection(
                List.of(
                    PermissionExpression.relation(VIEWER),
                    PermissionExpression.relation(EDITOR))));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(policy, lookup, new EvaluationLimits(32, 1));

    CheckDecision decision = evaluator.bulkCheck(List.of(request(VIEW))).get(0);

    assertThat(lookup.batchSizes()).containsExactly(2);
    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.LIMIT_EXCEEDED);
  }

  @Test
  void bulkCheckDerivesFreshTimeoutForEachDecision() {
    ObjectRef secondDocument = new ObjectRef("document", "doc-2");
    List<Instant> deadlines = new ArrayList<>();
    RelationshipLookup lookup =
        new RelationshipLookup() {
          @Override
          public Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
              List<RelationLookupRequest> requests) {
            return Map.of();
          }

          @Override
          public Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
              List<RelationLookupRequest> requests, Optional<Instant> deadline) {
            if (requests.size() > 1) {
              throw new IllegalStateException("batch lookup failed");
            }
            deadlines.add(deadline.orElseThrow());
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
            return Map.of(requests.get(0), List.of(RelationshipEntry.subject(ALICE)));
          }
        };
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            relationPolicy(),
            lookup,
            new EvaluationLimits(
                32, 1000, 100, 100, 10, Optional.of(Duration.ofSeconds(5))));

    List<CheckDecision> decisions =
        evaluator.bulkCheck(
            List.of(
                new CheckRequest(DOCUMENT, VIEW, ALICE),
                new CheckRequest(secondDocument, VIEW, ALICE)));

    assertThat(decisions).extracting(CheckDecision::allowed).containsExactly(true, true);
    assertThat(deadlines).hasSize(2);
    assertThat(deadlines.get(1)).isAfter(deadlines.get(0));
  }

  @Test
  void bulkCheckFallsBackAfterPrefetchResolverFailure() {
    AtomicInteger calls = new AtomicInteger();
    RelationshipLookup lookup =
        requests -> {
          calls.incrementAndGet();
          if (requests.size() > 1) {
            throw new IllegalStateException("batch lookup failed");
          }
          Map<RelationLookupRequest, List<RelationshipEntry>> resolved = new HashMap<>();
          requests.forEach(
              request -> resolved.put(request, List.of(RelationshipEntry.subject(ALICE))));
          return resolved;
        };
    ObjectRef secondDocument = new ObjectRef("document", "doc-2");

    List<CheckDecision> decisions =
        evaluator(relationPolicy(), lookup)
            .bulkCheck(
                List.of(
                    new CheckRequest(DOCUMENT, VIEW, ALICE),
                    new CheckRequest(secondDocument, VIEW, ALICE)));

    assertThat(decisions).extracting(CheckDecision::allowed).containsExactly(true, true);
    assertThat(calls).hasValue(3);
  }

  @Test
  void allowsIntersectionWhenEveryBranchMatches() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(DOCUMENT, EDITOR), RelationshipEntry.subject(ALICE));
    CompiledPolicy policy =
        policy(
            PermissionExpression.intersection(
                List.of(
                    PermissionExpression.relation(VIEWER),
                    PermissionExpression.relation(EDITOR))));

    CheckDecision decision = evaluator(policy, lookup).check(request(VIEW));

    assertThat(decision.allowed()).isTrue();
  }

  @Test
  void allowedProofExcludesEvidenceFromFailedUnionBranch() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(DOCUMENT, EDITOR), RelationshipEntry.subject(ALICE));
    CompiledPolicy policy =
        policy(
            PermissionExpression.union(
                List.of(
                    PermissionExpression.intersection(
                        List.of(
                            PermissionExpression.relation(VIEWER),
                            PermissionExpression.relation(BLOCKED))),
                    PermissionExpression.relation(EDITOR))));

    CheckDecision decision = evaluator(policy, lookup).check(request(VIEW));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.proof())
        .containsExactly(new ProofStep(DOCUMENT, EDITOR, ALICE));
  }

  @Test
  void deniesExclusionWhenExcludedBranchMatches() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    lookup.put(new RelationLookupRequest(DOCUMENT, BLOCKED), RelationshipEntry.subject(ALICE));
    CompiledPolicy policy =
        policy(
            PermissionExpression.exclusion(
                PermissionExpression.relation(VIEWER),
                PermissionExpression.relation(BLOCKED)));

    CheckDecision decision = evaluator(policy, lookup).check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.NO_MATCH);
  }

  @Test
  void allowsTraversalThroughRelatedObject() {
    ObjectRef folder = new ObjectRef("folder", "folder-1");
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(DOCUMENT, PARENT),
        RelationshipEntry.subjectSet(new SubjectSetRef(folder, MEMBER)));
    lookup.put(new RelationLookupRequest(folder, MEMBER), RelationshipEntry.subject(ALICE));
    CompiledPolicy policy =
        policy(
            PermissionExpression.traversal(
                PARENT, PermissionExpression.relation(MEMBER)));

    CheckDecision decision = evaluator(policy, lookup).check(request(VIEW));

    assertThat(decision.allowed()).isTrue();
  }

  @Test
  void deniesUnknownPermission() {
    CheckDecision decision = evaluator(relationPolicy(), new CountingLookup())
        .check(request(new PermissionRef("missing")));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.UNKNOWN_PERMISSION);
  }

  @Test
  void evaluatesCaveatsAgainstRequestAttributes() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    CaveatRef active = new CaveatRef("active");
    AttributeRef status = new AttributeRef("status");
    CompiledPolicy policy =
        policy(PermissionExpression.caveat(PermissionExpression.relation(VIEWER), active));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            policy,
            lookup,
            EvaluationLimits.defaults(),
            (caveat, request) ->
                active.equals(caveat) && "active".equals(request.attributes().get(status)));

    CheckDecision decision =
        evaluator.check(new CheckRequest(DOCUMENT, VIEW, ALICE, Map.of(status, "active")));

    assertThat(decision.allowed()).isTrue();
  }

  @Test
  void deniesWhenCaveatDoesNotPass() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(new RelationLookupRequest(DOCUMENT, VIEWER), RelationshipEntry.subject(ALICE));
    CompiledPolicy policy =
        policy(
            PermissionExpression.caveat(
                PermissionExpression.relation(VIEWER), new CaveatRef("active")));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            policy, lookup, EvaluationLimits.defaults(), (caveat, request) -> false);

    CheckDecision decision = evaluator.check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(lookup.calls()).isEqualTo(0);
  }

  @Test
  void failsClosedWhenResolverLimitIsExceeded() {
    CountingLookup lookup = new CountingLookup();
    ObjectRef group = new ObjectRef("group", "engineering");
    lookup.put(
        new RelationLookupRequest(DOCUMENT, VIEWER),
        RelationshipEntry.subjectSet(new SubjectSetRef(group, new RelationRef("member"))));
    CompiledPolicy policy = relationPolicy();
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(policy, lookup, new EvaluationLimits(32, 1));

    CheckDecision decision = evaluator.check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.LIMIT_EXCEEDED);
  }

  @Test
  void failsClosedWhenRelationshipCycleIsDetected() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(DOCUMENT, VIEWER),
        RelationshipEntry.subjectSet(new SubjectSetRef(DOCUMENT, VIEWER)));

    CheckDecision decision = evaluator(relationPolicy(), lookup).check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.CYCLE_DETECTED);
  }

  @Test
  void failsClosedWhenVisitedNodeLimitIsExceeded() {
    CompiledPolicy policy =
        policy(
            PermissionExpression.union(
                List.of(
                    PermissionExpression.relation(VIEWER),
                    PermissionExpression.relation(VIEWER))));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            policy,
            new CountingLookup(),
            new EvaluationLimits(32, 1000, 1, 100, 10, Optional.empty()));

    CheckDecision decision = evaluator.check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.LIMIT_EXCEEDED);
  }

  @Test
  void failsClosedWhenIntermediateResultLimitIsExceeded() {
    CountingLookup lookup = new CountingLookup();
    lookup.put(
        new RelationLookupRequest(DOCUMENT, VIEWER),
        List.of(
            RelationshipEntry.subject(ALICE),
            RelationshipEntry.subject(new SubjectRef("principal", "bob"))));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            relationPolicy(),
            lookup,
            new EvaluationLimits(32, 1000, 100, 1, 10, Optional.empty()));

    CheckDecision decision = evaluator.check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.LIMIT_EXCEEDED);
  }

  @Test
  void failsClosedWhenBatchSizeLimitIsExceeded() {
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            relationPolicy(),
            new CountingLookup(),
            new EvaluationLimits(32, 1000, 100, 100, 1, Optional.empty()));

    List<CheckDecision> decisions = evaluator.bulkCheck(List.of(request(VIEW), request(VIEW)));

    assertThat(decisions)
        .extracting(CheckDecision::reason)
        .containsExactly(DecisionReason.LIMIT_EXCEEDED, DecisionReason.LIMIT_EXCEEDED);
  }

  @Test
  void failsClosedWhenDeadlineIsExceeded() {
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            relationPolicy(),
            new CountingLookup(),
            new EvaluationLimits(32, 1000, 100, 100, 10, Optional.of(Duration.ZERO)));

    CheckDecision decision = evaluator.check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.DEADLINE_EXCEEDED);
  }

  @Test
  void derivesFreshDeadlineForEachEvaluation() throws InterruptedException {
    DeadlineLookup lookup = new DeadlineLookup();
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            relationPolicy(),
            lookup,
            new EvaluationLimits(
                32, 1000, 100, 100, 10, Optional.of(Duration.ofSeconds(5))));

    evaluator.check(request(VIEW));
    Thread.sleep(10);
    evaluator.check(request(VIEW));

    assertThat(lookup.deadlines).hasSize(2);
    assertThat(lookup.deadlines.get(1)).isAfter(lookup.deadlines.get(0));
  }

  @Test
  void failsClosedWhenDepthLimitIsExceeded() {
    CompiledPolicy policy =
        policy(
            PermissionExpression.union(
                List.of(
                    PermissionExpression.union(
                        List.of(
                            PermissionExpression.relation(VIEWER),
                            PermissionExpression.relation(EDITOR))),
                    PermissionExpression.relation(BLOCKED))));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(policy, new CountingLookup(), new EvaluationLimits(1, 1000));

    CheckDecision decision = evaluator.check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.LIMIT_EXCEEDED);
  }

  @Test
  void failsClosedWhenResolverThrows() {
    RelationshipLookup lookup =
        requests -> {
          throw new IllegalStateException("lookup failed");
        };
    CheckDecision decision = evaluator(relationPolicy(), lookup).check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE);
  }

  @Test
  void failsClosedWhenResolverReportsConsistencyConflict() {
    RelationshipLookup lookup =
        requests -> {
          throw new RelationshipLookupException(
              DecisionReason.CONSISTENCY_CONFLICT, "stale consistency token");
        };
    CheckDecision decision = evaluator(relationPolicy(), lookup).check(request(VIEW));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.CONSISTENCY_CONFLICT);
  }

  private static AuthorizationEvaluator evaluator(CompiledPolicy policy, CountingLookup lookup) {
    return new AuthorizationEvaluator(policy, lookup, EvaluationLimits.defaults());
  }

  private static AuthorizationEvaluator evaluator(
      CompiledPolicy policy, RelationshipLookup lookup) {
    return new AuthorizationEvaluator(policy, lookup, EvaluationLimits.defaults());
  }

  private static CompiledPolicy relationPolicy() {
    return policy(PermissionExpression.relation(VIEWER));
  }

  private static CompiledPolicy policy(PermissionExpression expression) {
    return PolicyCompiler.compile(
        new PolicyDefinition(Map.of(VIEW, expression)),
        ResolverCapabilities.of(
            List.of(VIEWER, EDITOR, BLOCKED, PARENT, MEMBER),
            List.of(new CaveatRef("active"))));
  }

  private static CheckRequest request(PermissionRef permission) {
    return new CheckRequest(DOCUMENT, permission, ALICE);
  }

  private static final class CountingLookup implements RelationshipLookup {

    private final Map<RelationLookupRequest, List<RelationshipEntry>> entries = new HashMap<>();

    private int calls;

    private final List<Integer> batchSizes = new ArrayList<>();

    void put(RelationLookupRequest request, RelationshipEntry entry) {
      entries.put(request, List.of(entry));
    }

    void put(RelationLookupRequest request, List<RelationshipEntry> entry) {
      entries.put(request, List.copyOf(entry));
    }

    int calls() {
      return calls;
    }

    List<Integer> batchSizes() {
      return List.copyOf(batchSizes);
    }

    @Override
    public Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
        List<RelationLookupRequest> requests) {
      calls++;
      batchSizes.add(requests.size());
      Map<RelationLookupRequest, List<RelationshipEntry>> result = new HashMap<>();
      requests.forEach(request -> result.put(request, entries.getOrDefault(request, List.of())));
      return result;
    }
  }

  private static final class DeadlineLookup implements RelationshipLookup {

    private final List<Instant> deadlines = new ArrayList<>();

    @Override
    public Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
        List<RelationLookupRequest> requests) {
      return Map.of();
    }

    @Override
    public Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
        List<RelationLookupRequest> requests, Optional<Instant> deadline) {
      deadlines.add(deadline.orElseThrow());
      return Map.of(requests.get(0), List.of(RelationshipEntry.subject(ALICE)));
    }
  }
}
