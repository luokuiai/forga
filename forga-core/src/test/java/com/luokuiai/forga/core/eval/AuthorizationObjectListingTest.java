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
import com.luokuiai.forga.core.policy.ResolverCapabilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthorizationObjectListingTest {

  private static final ObjectRef DOCUMENT_1 = new ObjectRef("document", "doc-1");

  private static final ObjectRef DOCUMENT_2 = new ObjectRef("document", "doc-2");

  private static final ObjectRef DOCUMENT_3 = new ObjectRef("document", "doc-3");

  private static final ObjectRef FOLDER = new ObjectRef("folder", "folder-1");

  private static final SubjectRef ALICE = new SubjectRef("principal", "alice");

  private static final SubjectRef BOB = new SubjectRef("principal", "bob");

  private static final RelationRef VIEWER = new RelationRef("viewer");

  private static final RelationRef EDITOR = new RelationRef("editor");

  private static final RelationRef BLOCKED = new RelationRef("blocked");

  private static final RelationRef PARENT = new RelationRef("parent");

  private static final RelationRef MEMBER = new RelationRef("member");

  private static final PermissionRef VIEW = new PermissionRef("view");

  private static final PermissionRef EDIT = new PermissionRef("edit");

  private static final int DEFAULT_REVERSE_LIMIT = 10_000;

  @Test
  void listsObjectsFromReverseRelationLookup() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_2, DOCUMENT_1));

    ListObjectsResponse response = evaluator(relationPolicy(), listing).listObjects(request());

    assertThat(response.successful()).isTrue();
    assertThat(response.objects()).containsExactly(DOCUMENT_1, DOCUMENT_2);
  }

  @Test
  void listedObjectPassesCheckWithSamePolicy() {
    CountingLookup forward = new CountingLookup();
    forward.put(new RelationLookupRequest(DOCUMENT_1, VIEWER), RelationshipEntry.subject(ALICE));
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(relationPolicy(), forward, listing, EvaluationLimits.defaults());

    ListObjectsResponse response = evaluator.listObjects(request());

    assertThat(response.objects()).containsExactly(DOCUMENT_1);
    assertThat(evaluator.check(new CheckRequest(DOCUMENT_1, VIEW, ALICE)).allowed()).isTrue();
  }

  @Test
  void unionListingDeduplicatesObjectsAndCachesReverseLookups() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1, DOCUMENT_1));
    CompiledPolicy policy =
        policy(
            PermissionExpression.union(
                List.of(
                    PermissionExpression.relation(VIEWER),
                    PermissionExpression.relation(VIEWER))));

    ListObjectsResponse response = evaluator(policy, listing).listObjects(request());

    assertThat(response.objects()).containsExactly(DOCUMENT_1);
    assertThat(listing.calls()).isEqualTo(1);
  }

  @Test
  void intersectionListingReturnsObjectsPresentInEveryBranch() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1, DOCUMENT_2));
    listing.put(direct("document", EDITOR), List.of(DOCUMENT_2));
    CompiledPolicy policy =
        policy(
            PermissionExpression.intersection(
                List.of(
                    PermissionExpression.relation(VIEWER),
                    PermissionExpression.relation(EDITOR))));

    ListObjectsResponse response = evaluator(policy, listing).listObjects(request());

    assertThat(response.objects()).containsExactly(DOCUMENT_2);
  }

  @Test
  void exclusionListingSubtractsExcludedBranch() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1, DOCUMENT_2));
    listing.put(direct("document", BLOCKED), List.of(DOCUMENT_2));
    CompiledPolicy policy =
        policy(
            PermissionExpression.exclusion(
                PermissionExpression.relation(VIEWER),
                PermissionExpression.relation(BLOCKED)));

    ListObjectsResponse response = evaluator(policy, listing).listObjects(request());

    assertThat(response.objects()).containsExactly(DOCUMENT_1);
  }

  @Test
  void traversalListingUsesDeclaredIntermediateObjectType() {
    CountingLookup forward = new CountingLookup();
    forward.put(
        new RelationLookupRequest(DOCUMENT_1, PARENT),
        RelationshipEntry.subjectSet(new SubjectSetRef(FOLDER, MEMBER)));
    forward.put(new RelationLookupRequest(FOLDER, MEMBER), RelationshipEntry.subject(ALICE));
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("folder", MEMBER), List.of(FOLDER));
    listing.put(
        subjectSet("document", PARENT, new SubjectSetRef(FOLDER, MEMBER)),
        List.of(DOCUMENT_1));
    CompiledPolicy policy =
        policy(
            PermissionExpression.traversal(
                PARENT, "folder", PermissionExpression.relation(MEMBER)));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(policy, forward, listing, EvaluationLimits.defaults());

    ListObjectsResponse response = evaluator.listObjects(request());

    assertThat(response.objects()).containsExactly(DOCUMENT_1);
    assertThat(evaluator.check(new CheckRequest(DOCUMENT_1, VIEW, ALICE)).allowed()).isTrue();
  }

  @Test
  void caveatListingUsesRequestAttributes() {
    AttributeRef status = new AttributeRef("status");
    CaveatRef active = new CaveatRef("active");
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1));
    CompiledPolicy policy =
        policy(PermissionExpression.caveat(PermissionExpression.relation(VIEWER), active));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            policy,
            new CountingLookup(),
            listing,
            EvaluationLimits.defaults(),
            (caveat, request) ->
                active.equals(caveat) && "active".equals(request.attributes().get(status)));
    ListObjectsRequest request =
        new ListObjectsRequest(
            "document", VIEW, ALICE, 10, Optional.empty(), Map.of(status, "active"));

    ListObjectsResponse response = evaluator.listObjects(request);

    assertThat(response.objects()).containsExactly(DOCUMENT_1);
  }

  @Test
  void listingFailsClosedWhenPageSizeExceedsBatchLimit() {
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            relationPolicy(),
            new CountingLookup(),
            new CountingListingLookup(),
            new EvaluationLimits(32, 1000, 100, 100, 1, Optional.empty()));

    ListObjectsResponse response =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 2));

    assertThat(response.successful()).isFalse();
    assertThat(response.reason()).isEqualTo(DecisionReason.LIMIT_EXCEEDED);
  }

  @Test
  void listingFailsClosedWhenReverseLookupIsUnavailable() {
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            relationPolicy(), new CountingLookup(), EvaluationLimits.defaults());

    ListObjectsResponse response = evaluator.listObjects(request());

    assertThat(response.successful()).isFalse();
    assertThat(response.reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE);
  }

  @Test
  void listingFailsClosedWhenResolverCallLimitIsExceeded() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1));
    listing.put(direct("document", EDITOR), List.of(DOCUMENT_2));
    CompiledPolicy policy =
        policy(
            PermissionExpression.union(
                List.of(
                    PermissionExpression.relation(VIEWER),
                    PermissionExpression.relation(EDITOR))));
    AuthorizationEvaluator evaluator =
        new AuthorizationEvaluator(
            policy, new CountingLookup(), listing, new EvaluationLimits(32, 1));

    ListObjectsResponse response = evaluator.listObjects(request());

    assertThat(response.successful()).isFalse();
    assertThat(response.reason()).isEqualTo(DecisionReason.LIMIT_EXCEEDED);
  }

  @Test
  void listingFailsClosedWhenResolverThrows() {
    ObjectListingLookup listing =
        requests -> {
          throw new RelationshipLookupException(
              DecisionReason.CONSISTENCY_CONFLICT, "stale consistency token");
        };

    ListObjectsResponse response = evaluator(relationPolicy(), listing).listObjects(request());

    assertThat(response.successful()).isFalse();
    assertThat(response.reason()).isEqualTo(DecisionReason.CONSISTENCY_CONFLICT);
  }

  @Test
  void paginatesWithStableBoundCursor() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1, DOCUMENT_2, DOCUMENT_3));
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), listing);

    ListObjectsResponse first =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 1));
    ListObjectsResponse second =
        evaluator.listObjects(
            new ListObjectsRequest(
                "document", VIEW, ALICE, 1, first.nextCursor(), Map.of()));

    assertThat(first.objects()).containsExactly(DOCUMENT_1);
    assertThat(first.nextCursor()).isPresent();
    assertThat(second.objects()).containsExactly(DOCUMENT_2);
    assertThat(second.nextCursor()).isPresent();
  }

  @Test
  void rejectsCursorBoundToAnotherSubject() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1, DOCUMENT_2));
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), listing);

    ListObjectsResponse first =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 1));
    ListObjectsResponse reused =
        evaluator.listObjects(
            new ListObjectsRequest("document", VIEW, BOB, 1, first.nextCursor(), Map.of()));

    assertThat(reused.successful()).isFalse();
    assertThat(reused.reason()).isEqualTo(DecisionReason.INVALID_CURSOR);
  }

  @Test
  void rejectsModifiedCursor() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1, DOCUMENT_2));
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), listing);
    ListObjectsResponse first =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 1));
    String token = first.nextCursor().orElseThrow().token();
    char replacement = token.charAt(token.length() - 1) == 'A' ? 'B' : 'A';
    ListObjectsCursor modified =
        new ListObjectsCursor(token.substring(0, token.length() - 1) + replacement);

    ListObjectsResponse response =
        evaluator.listObjects(
            new ListObjectsRequest(
                "document", VIEW, ALICE, 1, Optional.of(modified), Map.of()));

    assertThat(response.successful()).isFalse();
    assertThat(response.reason()).isEqualTo(DecisionReason.INVALID_CURSOR);
  }

  @Test
  void finalPageHasNoCursorAndNoDuplicates() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1, DOCUMENT_1, DOCUMENT_2));
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), listing);

    ListObjectsResponse first =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 1));
    ListObjectsResponse second =
        evaluator.listObjects(
            new ListObjectsRequest(
                "document", VIEW, ALICE, 1, first.nextCursor(), Map.of()));

    assertThat(second.objects()).containsExactly(DOCUMENT_2);
    assertThat(second.nextCursor()).isEmpty();
  }

  @Test
  void rejectsCursorBoundToAnotherPermission() {
    CountingListingLookup listing = new CountingListingLookup();
    listing.put(direct("document", VIEWER), List.of(DOCUMENT_1, DOCUMENT_2));
    AuthorizationEvaluator evaluator = evaluator(policyWithViewAndEdit(), listing);

    ListObjectsResponse first =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 1));
    ListObjectsResponse reused =
        evaluator.listObjects(
            new ListObjectsRequest("document", EDIT, ALICE, 1, first.nextCursor(), Map.of()));

    assertThat(reused.successful()).isFalse();
    assertThat(reused.reason()).isEqualTo(DecisionReason.INVALID_CURSOR);
  }

  @Test
  void cursorPropagatesEstablishedConsistencyToNextReverseLookup() {
    ConsistencyToken token = new ConsistencyToken("snapshot-1");
    ListObjectsCursor resolverCursor = new ListObjectsCursor("resolver-page-2");
    CapturingListingLookup listing =
        new CapturingListingLookup(
            new ObjectListingPage(
                List.of(DOCUMENT_1, DOCUMENT_2),
                Optional.of(resolverCursor),
                Optional.of(token)));
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), listing);

    ListObjectsResponse first =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 1));
    evaluator.listObjects(
        new ListObjectsRequest("document", VIEW, ALICE, 1, first.nextCursor(), Map.of()));

    assertThat(listing.requests()).hasSize(2);
    assertThat(listing.requests().get(1).consistency()).contains(token);
  }

  @Test
  void cursorPropagatesResolverContinuationState() {
    ListObjectsCursor resolverCursor = new ListObjectsCursor("resolver-page-2");
    CapturingListingLookup listing =
        new CapturingListingLookup(
            new ObjectListingPage(
                List.of(DOCUMENT_1, DOCUMENT_2), Optional.of(resolverCursor)));
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), listing);

    ListObjectsResponse first =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 1));
    ListObjectsResponse second =
        evaluator.listObjects(
        new ListObjectsRequest("document", VIEW, ALICE, 1, first.nextCursor(), Map.of()));
    evaluator.listObjects(
        new ListObjectsRequest("document", VIEW, ALICE, 1, second.nextCursor(), Map.of()));

    assertThat(listing.requests()).hasSize(3);
    assertThat(listing.requests().get(1).cursor()).isEmpty();
    assertThat(listing.requests().get(2).cursor()).contains(resolverCursor);
  }

  @Test
  void returnsEveryObjectAcrossResolverContinuationPages() {
    ListObjectsCursor resolverCursor = new ListObjectsCursor("resolver-page-2");
    ObjectListingLookup listing =
        requests -> {
          ReverseRelationLookupRequest request = requests.get(0);
          ObjectListingPage page =
              request.cursor().isEmpty()
                  ? new ObjectListingPage(
                      List.of(DOCUMENT_1, DOCUMENT_2), Optional.of(resolverCursor))
                  : new ObjectListingPage(List.of(DOCUMENT_3));
          return Map.of(request, page);
        };
    AuthorizationEvaluator evaluator = evaluator(relationPolicy(), listing);

    ListObjectsResponse first =
        evaluator.listObjects(new ListObjectsRequest("document", VIEW, ALICE, 1));
    ListObjectsResponse second =
        evaluator.listObjects(
            new ListObjectsRequest(
                "document", VIEW, ALICE, 1, first.nextCursor(), Map.of()));
    ListObjectsResponse third =
        evaluator.listObjects(
            new ListObjectsRequest(
                "document", VIEW, ALICE, 1, second.nextCursor(), Map.of()));

    assertThat(first.objects()).containsExactly(DOCUMENT_1);
    assertThat(second.objects()).containsExactly(DOCUMENT_2);
    assertThat(third.objects()).containsExactly(DOCUMENT_3);
    assertThat(third.nextCursor()).isEmpty();
  }

  @Test
  void listingFailsClosedWhenConsistencyTokensConflict() {
    ConsistencyToken first = new ConsistencyToken("snapshot-1");
    ConsistencyToken second = new ConsistencyToken("snapshot-2");
    ObjectListingLookup listing =
        requests ->
            Map.of(
                requests.get(0),
                VIEWER.equals(requests.get(0).relation())
                    ? new ObjectListingPage(
                        List.of(DOCUMENT_1), Optional.empty(), Optional.of(first))
                    : new ObjectListingPage(
                        List.of(DOCUMENT_2), Optional.empty(), Optional.of(second)));
    CompiledPolicy policy =
        policy(
            PermissionExpression.union(
                List.of(
                    PermissionExpression.relation(VIEWER),
                    PermissionExpression.relation(EDITOR))));

    ListObjectsResponse response = evaluator(policy, listing).listObjects(request());

    assertThat(response.successful()).isFalse();
    assertThat(response.reason()).isEqualTo(DecisionReason.CONSISTENCY_CONFLICT);
  }

  private static AuthorizationEvaluator evaluator(
      CompiledPolicy policy, ObjectListingLookup listing) {
    return new AuthorizationEvaluator(
        policy, new CountingLookup(), listing, EvaluationLimits.defaults());
  }

  private static CompiledPolicy relationPolicy() {
    return policy(PermissionExpression.relation(VIEWER));
  }

  private static CompiledPolicy policyWithViewAndEdit() {
    return PolicyCompiler.compile(
        new PolicyDefinition(
            Map.of(
                VIEW, PermissionExpression.relation(VIEWER),
                EDIT, PermissionExpression.relation(VIEWER))),
        ResolverCapabilities.of(List.of(VIEWER), List.of()));
  }

  private static CompiledPolicy policy(PermissionExpression expression) {
    return PolicyCompiler.compile(
        new PolicyDefinition(Map.of(VIEW, expression)),
        ResolverCapabilities.of(
            List.of(VIEWER, EDITOR, BLOCKED, PARENT, MEMBER),
            List.of(new CaveatRef("active"))));
  }

  private static ListObjectsRequest request() {
    return new ListObjectsRequest("document", VIEW, ALICE, 10);
  }

  private static ReverseRelationLookupRequest direct(String objectType, RelationRef relation) {
    return new ReverseRelationLookupRequest(
        objectType, relation, new DirectReverseLookupSubject(ALICE), DEFAULT_REVERSE_LIMIT);
  }

  private static ReverseRelationLookupRequest subjectSet(
      String objectType, RelationRef relation, SubjectSetRef subjectSet) {
    return new ReverseRelationLookupRequest(
        objectType,
        relation,
        new SubjectSetReverseLookupSubject(subjectSet),
        DEFAULT_REVERSE_LIMIT);
  }

  private static final class CountingLookup implements RelationshipLookup {

    private final Map<RelationLookupRequest, List<RelationshipEntry>> entries = new HashMap<>();

    void put(RelationLookupRequest request, RelationshipEntry entry) {
      entries.put(request, List.of(entry));
    }

    @Override
    public Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
        List<RelationLookupRequest> requests) {
      Map<RelationLookupRequest, List<RelationshipEntry>> result = new HashMap<>();
      requests.forEach(request -> result.put(request, entries.getOrDefault(request, List.of())));
      return result;
    }
  }

  private static final class CountingListingLookup implements ObjectListingLookup {

    private final Map<ReverseRelationLookupRequest, ObjectListingPage> pages = new HashMap<>();

    private int calls;

    void put(ReverseRelationLookupRequest request, List<ObjectRef> objects) {
      pages.put(request, new ObjectListingPage(objects));
    }

    void put(ReverseRelationLookupRequest request, ObjectListingPage page) {
      pages.put(request, page);
    }

    int calls() {
      return calls;
    }

    @Override
    public Map<ReverseRelationLookupRequest, ObjectListingPage> resolve(
        List<ReverseRelationLookupRequest> requests) {
      calls++;
      Map<ReverseRelationLookupRequest, ObjectListingPage> result = new HashMap<>();
      requests.forEach(request -> result.put(request, pages.get(request)));
      return result;
    }
  }

  private static final class CapturingListingLookup implements ObjectListingLookup {

    private final ObjectListingPage page;

    private final List<ReverseRelationLookupRequest> requests = new ArrayList<>();

    CapturingListingLookup(ObjectListingPage page) {
      this.page = page;
    }

    List<ReverseRelationLookupRequest> requests() {
      return List.copyOf(requests);
    }

    @Override
    public Map<ReverseRelationLookupRequest, ObjectListingPage> resolve(
        List<ReverseRelationLookupRequest> requests) {
      this.requests.addAll(requests);
      Map<ReverseRelationLookupRequest, ObjectListingPage> result = new HashMap<>();
      requests.forEach(request -> result.put(request, page));
      return result;
    }
  }
}
