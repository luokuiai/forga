package com.luokuiai.forga.core.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.model.ConsistencyToken;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PermissionGrantEvaluationTest {

  private static final ObjectRef DOCUMENT = new ObjectRef("document", "doc-1");

  private static final SubjectRef ALICE = new SubjectRef("principal", "alice");

  private static final PermissionRef VIEW = new PermissionRef("view");

  private static final RelationRef OWNER = new RelationRef("owner");

  private static final RelationRef PARENT = new RelationRef("parent");

  @Test
  void resolvesAllowedAndDeniedEffectiveGrants() {
    PermissionGrantLookup grants =
        requests ->
            requests.stream()
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        request -> request,
                        request -> new PermissionGrantResult(ALICE.equals(request.subject()))));
    AuthorizationEvaluator evaluator =
        evaluator(policy(PermissionExpression.grant()), empty(), grants);

    CheckDecision allowed = evaluator.check(request(ALICE));
    CheckDecision denied = evaluator.check(request(new SubjectRef("principal", "bob")));

    assertThat(allowed.allowed()).isTrue();
    assertThat(allowed.proof()).isEmpty();
    assertThat(denied.allowed()).isFalse();
    assertThat(denied.reason()).isEqualTo(DecisionReason.NO_MATCH);
  }

  @Test
  void composesGrantWithRelationshipBranches() {
    RelationshipLookup relationships =
        requests -> Map.of(new RelationLookupRequest(DOCUMENT, OWNER), List.of());
    AuthorizationEvaluator evaluator =
        evaluator(
            policy(
                PermissionExpression.union(
                    List.of(
                        PermissionExpression.relation(OWNER),
                        PermissionExpression.grant()))),
            relationships,
            requests -> Map.of(requests.get(0), new PermissionGrantResult(true)));

    assertThat(evaluator.check(request(ALICE)).allowed()).isTrue();
  }

  @Test
  void bulkCheckResolvesDistinctGrantsInOneBatch() {
    AtomicInteger calls = new AtomicInteger();
    List<Integer> batchSizes = new ArrayList<>();
    PermissionGrantLookup grants =
        requests -> {
          calls.incrementAndGet();
          batchSizes.add(requests.size());
          return requests.stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      request -> request, request -> new PermissionGrantResult(true)));
        };
    AuthorizationEvaluator evaluator =
        evaluator(policy(PermissionExpression.grant()), empty(), grants);
    CheckRequest alice = request(ALICE);
    CheckRequest bob = request(new SubjectRef("principal", "bob"));

    List<CheckDecision> decisions = evaluator.bulkCheck(List.of(alice, bob));

    assertThat(decisions).allMatch(CheckDecision::allowed);
    assertThat(calls).hasValue(1);
    assertThat(batchSizes).containsExactly(2);
  }

  @Test
  void malformedOrFailedGrantLookupFailsClosed() {
    AuthorizationEvaluator incomplete =
        evaluator(policy(PermissionExpression.grant()), empty(), requests -> Map.of());
    AuthorizationEvaluator failed =
        evaluator(
            policy(PermissionExpression.grant()),
            empty(),
            requests -> {
              throw new IllegalStateException("unavailable");
            });

    assertThat(incomplete.check(request(ALICE)).reason())
        .isEqualTo(DecisionReason.RESOLVER_FAILURE);
    assertThat(failed.check(request(ALICE)).reason())
        .isEqualTo(DecisionReason.RESOLVER_FAILURE);
  }

  @Test
  void conflictingGrantConsistencyFailsClosed() {
    ObjectRef parent = new ObjectRef("folder", "one");
    RelationshipLookup relationships =
        requests ->
            Map.of(
                new RelationLookupRequest(DOCUMENT, PARENT),
                List.of(RelationshipEntry.subjectSet(new SubjectSetRef(parent, OWNER))));
    CompiledPolicy policy =
        policy(
            PermissionExpression.intersection(
                List.of(
                    PermissionExpression.grant(),
                    PermissionExpression.traversal(PARENT, PermissionExpression.grant()))));
    PermissionGrantLookup grants =
        requests ->
            requests.stream()
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        request -> request,
                        request ->
                            new PermissionGrantResult(
                                true,
                                Optional.of(
                                    new ConsistencyToken(
                                        request.object().equals(DOCUMENT) ? "v1" : "v2")))));

    CheckDecision decision = evaluator(policy, relationships, grants).check(request(ALICE));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.CONSISTENCY_CONFLICT);
  }

  @Test
  void legacyEvaluatorConstructorDeniesGrantExpression() {
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            policy(PermissionExpression.grant()), empty(), EvaluationLimits.defaults());

    assertThat(evaluator.check(request(ALICE)).allowed()).isFalse();
  }

  private static AuthorizationEvaluator evaluator(
      CompiledPolicy policy, RelationshipLookup relationships, PermissionGrantLookup grants) {
    return new AuthorizationEvaluator(
        policy,
        relationships,
        null,
        EvaluationLimits.defaults(),
        (caveat, request) -> false,
        grants);
  }

  private static RelationshipLookup empty() {
    return requests ->
        requests.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    request -> request, request -> List.of()));
  }

  private static CheckRequest request(SubjectRef subject) {
    return new CheckRequest(DOCUMENT, VIEW, subject);
  }

  private static CompiledPolicy policy(PermissionExpression expression) {
    return PolicyCompiler.compile(
        new PolicyDefinition(Map.of(VIEW, expression)),
        ResolverCapabilities.of(List.of(OWNER, PARENT), List.of()));
  }
}
