package com.luokuiai.forga.core.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.CaveatRef;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AttributeEvaluationTest {

  private static final ObjectRef DOCUMENT_1 = new ObjectRef("document", "one");

  private static final ObjectRef DOCUMENT_2 = new ObjectRef("document", "two");

  private static final ObjectRef FOLDER = new ObjectRef("folder", "shared");

  private static final SubjectRef ALICE = new SubjectRef("principal", "alice");

  private static final PermissionRef VIEW = new PermissionRef("view");

  private static final RelationRef VIEWER = new RelationRef("viewer");

  private static final RelationRef PARENT = new RelationRef("parent");

  private static final RelationRef MEMBER = new RelationRef("member");

  private static final CaveatRef ACTIVE = new CaveatRef("active");

  private static final AttributeRef STATUS = new AttributeRef("status");

  @Test
  void resolvesObjectAttributesForSingleCheck() {
    MapRelationshipLookup relationships = new MapRelationshipLookup();
    relationships.put(DOCUMENT_1, VIEWER, RelationshipEntry.subject(ALICE));
    CapturingAttributeLookup attributes = new CapturingAttributeLookup();
    attributes.put(DOCUMENT_1, Map.of(STATUS, "active"));
    AtomicReference<CaveatEvaluationContext> captured = new AtomicReference<>();
    AuthorizationEvaluator evaluator =
        evaluator(
            caveat(PermissionExpression.relation(VIEWER)),
            relationships,
            null,
            attributes,
            activeCaveat(captured));

    CheckDecision decision = evaluator.check(new CheckRequest(DOCUMENT_1, VIEW, ALICE));

    assertThat(decision.allowed()).isTrue();
    assertThat(attributes.batchSizes).containsExactly(1);
    assertThat(captured.get().object()).isEqualTo(DOCUMENT_1);
    assertThat(captured.get().objectAttributes()).containsEntry(STATUS, "active");
  }

  @Test
  void resolvesAttributesForCurrentTraversalObject() {
    MapRelationshipLookup relationships = new MapRelationshipLookup();
    relationships.put(
        DOCUMENT_1,
        PARENT,
        RelationshipEntry.subjectSet(new SubjectSetRef(FOLDER, MEMBER)));
    relationships.put(FOLDER, MEMBER, RelationshipEntry.subject(ALICE));
    CapturingAttributeLookup attributes = new CapturingAttributeLookup();
    attributes.put(FOLDER, Map.of(STATUS, "active"));
    AtomicReference<CaveatEvaluationContext> captured = new AtomicReference<>();
    PermissionExpression expression =
        PermissionExpression.traversal(
            PARENT, caveat(PermissionExpression.relation(MEMBER)));
    AuthorizationEvaluator evaluator =
        evaluator(expression, relationships, null, attributes, activeCaveat(captured));

    CheckDecision decision = evaluator.check(new CheckRequest(DOCUMENT_1, VIEW, ALICE));

    assertThat(decision.allowed()).isTrue();
    assertThat(captured.get().request().object()).isEqualTo(DOCUMENT_1);
    assertThat(captured.get().object()).isEqualTo(FOLDER);
  }

  @Test
  void bulkChecksBatchCaveatAttributesAtOneFrontier() {
    MapRelationshipLookup relationships = new MapRelationshipLookup();
    relationships.put(DOCUMENT_1, VIEWER, RelationshipEntry.subject(ALICE));
    relationships.put(DOCUMENT_2, VIEWER, RelationshipEntry.subject(ALICE));
    CapturingAttributeLookup attributes = new CapturingAttributeLookup();
    attributes.put(DOCUMENT_1, Map.of(STATUS, "active"));
    attributes.put(DOCUMENT_2, Map.of(STATUS, "active"));
    AuthorizationEvaluator evaluator =
        evaluator(
            caveat(PermissionExpression.relation(VIEWER)),
            relationships,
            null,
            attributes,
            activeCaveat(new AtomicReference<>()));

    List<CheckDecision> decisions =
        evaluator.bulkCheck(
            List.of(
                new CheckRequest(DOCUMENT_1, VIEW, ALICE),
                new CheckRequest(DOCUMENT_2, VIEW, ALICE)));

    assertThat(decisions).extracting(CheckDecision::allowed).containsExactly(true, true);
    assertThat(attributes.batchSizes).containsExactly(2);
  }

  @Test
  void listingFiltersBoundedCandidatesWithResolvedAttributes() {
    ObjectListingLookup listing =
        requests ->
            Map.of(
                requests.get(0), new ObjectListingPage(List.of(DOCUMENT_1, DOCUMENT_2)));
    CapturingAttributeLookup attributes = new CapturingAttributeLookup();
    attributes.put(DOCUMENT_1, Map.of(STATUS, "active"));
    attributes.put(DOCUMENT_2, Map.of(STATUS, "disabled"));
    AuthorizationEvaluator evaluator =
        evaluator(
            caveat(PermissionExpression.relation(VIEWER)),
            new MapRelationshipLookup(),
            listing,
            attributes,
            activeCaveat(new AtomicReference<>()));

    ListObjectsResponse response =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 10));

    assertThat(response.successful()).isTrue();
    assertThat(response.objects()).containsExactly(DOCUMENT_1);
    assertThat(attributes.batchSizes).containsExactly(2);
  }

  @Test
  void incompleteAttributeBatchFailsClosed() {
    MapRelationshipLookup relationships = new MapRelationshipLookup();
    relationships.put(DOCUMENT_1, VIEWER, RelationshipEntry.subject(ALICE));
    AttributeLookup incomplete =
        (requests, context) -> BatchResolution.unversioned(Map.of());
    AuthorizationEvaluator evaluator =
        evaluator(
            caveat(PermissionExpression.relation(VIEWER)),
            relationships,
            null,
            incomplete,
            activeCaveat(new AtomicReference<>()));

    CheckDecision decision = evaluator.check(new CheckRequest(DOCUMENT_1, VIEW, ALICE));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE);
  }

  @Test
  void conflictingRelationshipAndAttributeConsistencyFailsClosed() {
    ConsistencyToken relationshipToken = new ConsistencyToken("relationship-snapshot");
    ConsistencyToken attributeToken = new ConsistencyToken("attribute-snapshot");
    RelationshipLookup relationships =
        (requests, context) ->
            new BatchResolution<>(
                Map.of(requests.get(0), List.of(RelationshipEntry.subject(ALICE))),
                Optional.of(relationshipToken));
    AttributeLookup attributes =
        (requests, context) ->
            new BatchResolution<>(
                Map.of(requests.get(0), Map.of(STATUS, "active")),
                Optional.of(attributeToken));
    PermissionExpression expression =
        PermissionExpression.intersection(
            List.of(
                PermissionExpression.relation(VIEWER),
                caveat(PermissionExpression.relation(VIEWER))));
    AuthorizationEvaluator evaluator =
        evaluator(
            expression,
            relationships,
            null,
            attributes,
            activeCaveat(new AtomicReference<>()));

    CheckDecision decision = evaluator.check(new CheckRequest(DOCUMENT_1, VIEW, ALICE));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(DecisionReason.CONSISTENCY_CONFLICT);
  }

  private static PermissionExpression caveat(PermissionExpression expression) {
    return PermissionExpression.caveat(expression, ACTIVE);
  }

  private static CaveatEvaluator activeCaveat(
      AtomicReference<CaveatEvaluationContext> captured) {
    return new CaveatEvaluator() {
      @Override
      public Set<CaveatRef> caveats() {
        return Set.of(ACTIVE);
      }

      @Override
      public Set<AttributeRef> requiredAttributes(CaveatRef caveat) {
        return Set.of(STATUS);
      }

      @Override
      public boolean evaluate(CaveatRef caveat, CaveatEvaluationContext context) {
        captured.set(context);
        return ACTIVE.equals(caveat)
            && "active".equals(context.objectAttributes().get(STATUS));
      }
    };
  }

  private static AuthorizationEvaluator evaluator(
      PermissionExpression expression,
      RelationshipLookup relationships,
      ObjectListingLookup listing,
      AttributeLookup attributes,
      CaveatEvaluator caveats) {
    CompiledPolicy policy =
        PolicyCompiler.compile(new PolicyDefinition(Map.of(VIEW, expression)));
    return new AuthorizationEvaluator(
        policy,
        relationships,
        listing,
        EvaluationLimits.defaults(),
        caveats,
        attributes,
        PermissionGrantLookup.denyAll());
  }

  private static final class MapRelationshipLookup implements RelationshipLookup {

    private final Map<RelationLookupRequest, List<RelationshipEntry>> values = new HashMap<>();

    void put(ObjectRef object, RelationRef relation, RelationshipEntry entry) {
      values.put(new RelationLookupRequest(object, relation), List.of(entry));
    }

    @Override
    public BatchResolution<RelationLookupRequest, List<RelationshipEntry>> resolve(
        List<RelationLookupRequest> requests, EvaluationReadContext context) {
      Map<RelationLookupRequest, List<RelationshipEntry>> resolved = new HashMap<>();
      requests.forEach(request -> resolved.put(request, values.getOrDefault(request, List.of())));
      return BatchResolution.unversioned(resolved);
    }
  }

  private static final class CapturingAttributeLookup implements AttributeLookup {

    private final Map<ObjectRef, Map<AttributeRef, String>> values = new HashMap<>();

    private final List<Integer> batchSizes = new ArrayList<>();

    void put(ObjectRef object, Map<AttributeRef, String> attributes) {
      values.put(object, Map.copyOf(attributes));
    }

    @Override
    public BatchResolution<AttributeLookupRequest, Map<AttributeRef, String>> resolve(
        List<AttributeLookupRequest> requests, EvaluationReadContext context) {
      batchSizes.add(requests.size());
      Map<AttributeLookupRequest, Map<AttributeRef, String>> resolved = new HashMap<>();
      requests.forEach(
          request -> resolved.put(request, values.getOrDefault(request.object(), Map.of())));
      return BatchResolution.unversioned(resolved);
    }
  }
}
