package com.luokuiai.forga.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.luokuiai.forga.core.eval.BatchResolution;
import com.luokuiai.forga.core.eval.DecisionReason;
import com.luokuiai.forga.core.eval.DirectReverseLookupSubject;
import com.luokuiai.forga.core.eval.EvaluationReadContext;
import com.luokuiai.forga.core.eval.ListObjectsCursor;
import com.luokuiai.forga.core.eval.ObjectListingPage;
import com.luokuiai.forga.core.eval.RelationLookupRequest;
import com.luokuiai.forga.core.eval.RelationshipEntry;
import com.luokuiai.forga.core.eval.RelationshipLookupException;
import com.luokuiai.forga.core.eval.ReverseRelationLookupRequest;
import com.luokuiai.forga.core.model.ConsistencyToken;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.model.SubjectSetRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ResolverRegistryLookupTest {

  private static final RelationRef VIEWER = new RelationRef("viewer");

  private static final RelationRef EDITOR = new RelationRef("editor");

  private static final SubjectRef ALICE = new SubjectRef("principal", "alice");

  @Test
  void forwardLookupRoutesBatchesAndConvertsSubjectShapes() {
    Instant deadline = Instant.now().plusSeconds(5);
    SubjectSetRef teamMembers =
        new SubjectSetRef(new ObjectRef("team", "engineering"), new RelationRef("member"));
    TestResolver viewerResolver =
        new TestResolver(
            "viewer-resolver",
            Set.of(VIEWER),
            Set.of(),
            batch ->
                new ForwardRelationshipBatchResponse(
                    batch.requests().stream()
                        .map(
                            request ->
                                new ForwardRelationshipResponse(
                                    request,
                                    List.of(
                                        new DirectSubject(ALICE),
                                        new SubjectSetSubject(teamMembers))))
                        .toList()),
            ignored -> null);
    TestResolver editorResolver =
        new TestResolver(
            "editor-resolver",
            Set.of(EDITOR),
            Set.of(),
            batch ->
                new ForwardRelationshipBatchResponse(
                    batch.requests().stream()
                        .map(request -> new ForwardRelationshipResponse(request, List.of()))
                        .toList()),
            ignored -> null);
    ResolverRegistryRelationshipLookup lookup =
        new ResolverRegistryRelationshipLookup(
            new ResolverRegistry(List.of(viewerResolver, editorResolver)));
    RelationLookupRequest viewer =
        new RelationLookupRequest(new ObjectRef("document", "one"), VIEWER);
    RelationLookupRequest editor =
        new RelationLookupRequest(new ObjectRef("document", "two"), EDITOR);

    BatchResolution<RelationLookupRequest, List<RelationshipEntry>> batch =
        lookup.resolve(
            List.of(viewer, editor, viewer),
            new EvaluationReadContext(Optional.empty(), Optional.of(deadline)));
    Map<RelationLookupRequest, List<RelationshipEntry>> result = batch.values();

    assertThat(viewerResolver.forwardCalls).isOne();
    assertThat(editorResolver.forwardCalls).isOne();
    assertThat(viewerResolver.lastForwardRequest.context().deadline())
        .contains(new ResolverDeadline(deadline));
    assertThat(result).containsOnlyKeys(viewer, editor);
    assertThat(result.get(viewer)).hasSize(2);
    assertThat(result.get(viewer).get(0).subject()).contains(ALICE);
    assertThat(result.get(viewer).get(1).subjectSet()).contains(teamMembers);
    assertThat(result.get(editor)).isEmpty();
  }

  @Test
  void reverseLookupPreservesCursorConsistencyAndBounds() {
    Instant deadline = Instant.now().plusSeconds(5);
    ConsistencyToken consistency = new ConsistencyToken("revision-7");
    ObjectRef document = new ObjectRef("document", "one");
    TestResolver resolver =
        new TestResolver(
            "reverse-resolver",
            Set.of(),
            Set.of(VIEWER),
            ignored -> null,
            batch ->
                new ReverseRelationshipBatchResponse(
                    batch.requests().stream()
                        .map(
                            request ->
                                new ReverseRelationshipResponse(
                                    request,
                                    List.of(document),
                                    Optional.of(new PageCursor("next-page")),
                                    ConsistencyContext.of(consistency)))
                        .toList()));
    ResolverRegistryObjectListingLookup lookup =
        new ResolverRegistryObjectListingLookup(new ResolverRegistry(List.of(resolver)));
    ReverseRelationLookupRequest request =
        new ReverseRelationLookupRequest(
            "document",
            VIEWER,
            new DirectReverseLookupSubject(ALICE),
            Optional.of(new ListObjectsCursor("current-page")),
            Optional.of(consistency),
            2_000);

    ObjectListingPage page =
        lookup.resolve(List.of(request), Optional.of(deadline)).get(request);

    assertThat(resolver.reverseCalls).isOne();
    assertThat(resolver.lastReverseRequest.limit()).isEqualTo(ResolverBounds.MAX_LIMIT);
    assertThat(resolver.lastReverseRequest.cursor()).contains(new PageCursor("current-page"));
    assertThat(resolver.lastReverseRequest.context().consistency().token()).contains(consistency);
    assertThat(resolver.lastReverseRequest.context().deadline())
        .contains(new ResolverDeadline(deadline));
    assertThat(resolver.lastReverseRequest.subject()).isEqualTo(new DirectSubject(ALICE));
    assertThat(page.objects()).containsExactly(document);
    assertThat(page.nextCursor()).contains(new ListObjectsCursor("next-page"));
    assertThat(page.consistency()).contains(consistency);
  }

  @Test
  void missingResolverCapabilityFailsClosed() {
    ResolverRegistryRelationshipLookup lookup =
        new ResolverRegistryRelationshipLookup(new ResolverRegistry(List.of()));
    RelationLookupRequest request =
        new RelationLookupRequest(new ObjectRef("document", "one"), VIEWER);

    assertThatExceptionOfType(RelationshipLookupException.class)
        .isThrownBy(
            () ->
                lookup.resolve(
                    List.of(request),
                    new EvaluationReadContext(Optional.empty(), Optional.empty())))
        .satisfies(
            exception -> assertThat(exception.reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE))
        .withMessageContaining("missing forward resolver");
  }

  @Test
  void malformedResolverBatchFailsClosed() {
    TestResolver resolver =
        new TestResolver(
            "malformed-resolver",
            Set.of(VIEWER),
            Set.of(),
            ignored -> new ForwardRelationshipBatchResponse(List.of()),
            ignored -> null);
    ResolverRegistryRelationshipLookup lookup =
        new ResolverRegistryRelationshipLookup(new ResolverRegistry(List.of(resolver)));
    RelationLookupRequest request =
        new RelationLookupRequest(new ObjectRef("document", "one"), VIEWER);

    assertThatExceptionOfType(RelationshipLookupException.class)
        .isThrownBy(
            () ->
                lookup.resolve(
                    List.of(request),
                    new EvaluationReadContext(Optional.empty(), Optional.empty())))
        .satisfies(
            exception -> assertThat(exception.reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE))
        .withMessageContaining("forward resolver failed");
  }

  @Test
  void mismatchedResolverResponseFailsClosed() {
    RelationLookupRequest request =
        new RelationLookupRequest(new ObjectRef("document", "one"), VIEWER);
    TestResolver resolver =
        new TestResolver(
            "mismatched-resolver",
            Set.of(VIEWER),
            Set.of(),
            ignored -> {
              ForwardRelationshipRequest unexpected =
                  new ForwardRelationshipRequest(
                      new ObjectRef("document", "other"), VIEWER, ResolverBounds.MAX_LIMIT);
              return new ForwardRelationshipBatchResponse(
                  List.of(new ForwardRelationshipResponse(unexpected, List.of())));
            },
            ignored -> null);
    ResolverRegistryRelationshipLookup lookup =
        new ResolverRegistryRelationshipLookup(new ResolverRegistry(List.of(resolver)));

    assertThatExceptionOfType(RelationshipLookupException.class)
        .isThrownBy(
            () ->
                lookup.resolve(
                    List.of(request),
                    new EvaluationReadContext(Optional.empty(), Optional.empty())))
        .satisfies(
            exception -> assertThat(exception.reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE))
        .withMessageContaining("unexpected response");
  }

  @Test
  void forwardLookupPropagatesEstablishedConsistencyAcrossResolvers() {
    ConsistencyToken token = new ConsistencyToken("revision-9");
    TestResolver viewerResolver =
        new TestResolver(
            "viewer-resolver",
            Set.of(VIEWER),
            Set.of(),
            batch ->
                new ForwardRelationshipBatchResponse(
                    batch.requests().stream()
                        .map(
                            request ->
                                new ForwardRelationshipResponse(
                                    request, List.of(), ConsistencyContext.of(token)))
                        .toList()),
            ignored -> null);
    TestResolver editorResolver =
        new TestResolver(
            "editor-resolver",
            Set.of(EDITOR),
            Set.of(),
            batch -> {
              assertThat(batch.requests().get(0).context().consistency().token()).contains(token);
              return new ForwardRelationshipBatchResponse(
                  batch.requests().stream()
                      .map(
                          request ->
                              new ForwardRelationshipResponse(
                                  request, List.of(), ConsistencyContext.empty()))
                      .toList());
            },
            ignored -> null);
    ResolverRegistryRelationshipLookup lookup =
        new ResolverRegistryRelationshipLookup(
            new ResolverRegistry(List.of(viewerResolver, editorResolver)));

    var resolved =
        lookup.resolve(
            List.of(
                new RelationLookupRequest(new ObjectRef("document", "one"), VIEWER),
                new RelationLookupRequest(new ObjectRef("document", "two"), EDITOR)),
            new EvaluationReadContext(Optional.empty(), Optional.empty()));

    assertThat(resolved.consistency()).contains(token);
  }

  @Test
  void forwardLookupRejectsConflictingResolverConsistency() {
    ConsistencyToken requested = new ConsistencyToken("revision-9");
    ConsistencyToken returned = new ConsistencyToken("revision-10");
    TestResolver resolver =
        new TestResolver(
            "viewer-resolver",
            Set.of(VIEWER),
            Set.of(),
            batch ->
                new ForwardRelationshipBatchResponse(
                    batch.requests().stream()
                        .map(
                            request ->
                                new ForwardRelationshipResponse(
                                    request, List.of(), ConsistencyContext.of(returned)))
                        .toList()),
            ignored -> null);
    ResolverRegistryRelationshipLookup lookup =
        new ResolverRegistryRelationshipLookup(new ResolverRegistry(List.of(resolver)));
    RelationLookupRequest request =
        new RelationLookupRequest(new ObjectRef("document", "one"), VIEWER);

    assertThatExceptionOfType(RelationshipLookupException.class)
        .isThrownBy(
            () ->
                lookup.resolve(
                    List.of(request),
                    new EvaluationReadContext(
                        Optional.of(requested), Optional.empty())))
        .satisfies(
            exception ->
                assertThat(exception.reason()).isEqualTo(DecisionReason.CONSISTENCY_CONFLICT));
  }

  private static final class TestResolver
      implements ForwardRelationshipResolver, ReverseRelationshipResolver {

    private final String name;

    private final Set<RelationRef> forwardRelations;

    private final Set<RelationRef> reverseRelations;

    private final Function<ForwardRelationshipBatchRequest, ForwardRelationshipBatchResponse>
        forward;

    private final Function<ReverseRelationshipBatchRequest, ReverseRelationshipBatchResponse>
        reverse;

    private int forwardCalls;

    private int reverseCalls;

    private ReverseRelationshipRequest lastReverseRequest;

    private ForwardRelationshipRequest lastForwardRequest;

    private TestResolver(
        String name,
        Set<RelationRef> forwardRelations,
        Set<RelationRef> reverseRelations,
        Function<ForwardRelationshipBatchRequest, ForwardRelationshipBatchResponse> forward,
        Function<ReverseRelationshipBatchRequest, ReverseRelationshipBatchResponse> reverse) {
      this.name = name;
      this.forwardRelations = Set.copyOf(forwardRelations);
      this.reverseRelations = Set.copyOf(reverseRelations);
      this.forward = forward;
      this.reverse = reverse;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Set<RelationRef> forwardRelations() {
      return forwardRelations;
    }

    @Override
    public Set<RelationRef> reverseRelations() {
      return reverseRelations;
    }

    @Override
    public ForwardRelationshipBatchResponse resolveForward(
        ForwardRelationshipBatchRequest request) {
      forwardCalls++;
      lastForwardRequest = request.requests().get(0);
      return forward.apply(request);
    }

    @Override
    public ReverseRelationshipBatchResponse resolveReverse(
        ReverseRelationshipBatchRequest request) {
      reverseCalls++;
      lastReverseRequest = request.requests().get(0);
      return reverse.apply(request);
    }
  }
}
