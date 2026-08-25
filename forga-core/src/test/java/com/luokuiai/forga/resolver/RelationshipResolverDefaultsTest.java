package com.luokuiai.forga.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ConsistencyToken;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RelationshipResolverDefaultsTest {

  private static final RelationRef VIEWER = new RelationRef("viewer");

  private static final DirectSubject SUBJECT =
      new DirectSubject(new SubjectRef("user", "alice"));

  private final RelationshipResolver resolver = new ForwardOnlyResolver();

  @Test
  void returnsCompleteEmptyReverseBatchInRequestOrder() {
    ResolverContext context = context("revision-1");
    ReverseRelationshipRequest first =
        new ReverseRelationshipRequest(
            "document", VIEWER, SUBJECT, Optional.empty(), 10, context);
    ReverseRelationshipRequest second =
        new ReverseRelationshipRequest(
            "folder", VIEWER, SUBJECT, Optional.empty(), 10, context);

    ReverseRelationshipBatchResponse response =
        resolver.resolveReverse(new ReverseRelationshipBatchRequest(List.of(first, second)));

    assertThat(response.responses()).extracting(ReverseRelationshipResponse::request)
        .containsExactly(first, second);
    assertThat(response.responses()).allSatisfy(item -> {
      assertThat(item.objects()).isEmpty();
      assertThat(item.nextCursor()).isEmpty();
      assertThat(item.consistency()).isEqualTo(context.consistency());
    });
  }

  @Test
  void returnsCompleteEmptyAttributeBatchInRequestOrder() {
    ResolverContext context = context("revision-2");
    AttributeResolutionRequest first =
        new AttributeResolutionRequest(
            new ObjectRef("document", "doc-1"), List.of(new AttributeRef("status")), context);
    AttributeResolutionRequest second =
        new AttributeResolutionRequest(
            new ObjectRef("folder", "folder-1"), List.of(new AttributeRef("region")), context);

    AttributeResolutionBatchResponse response =
        resolver.resolveAttributes(new AttributeResolutionBatchRequest(List.of(first, second)));

    assertThat(response.responses()).extracting(AttributeResolutionResponse::request)
        .containsExactly(first, second);
    assertThat(response.responses()).allSatisfy(item -> {
      assertThat(item.attributes()).isEmpty();
      assertThat(item.consistency()).isEqualTo(context.consistency());
    });
  }

  private static ResolverContext context(String token) {
    return new ResolverContext(
        ConsistencyContext.of(new ConsistencyToken(token)), Optional.empty());
  }

  private static final class ForwardOnlyResolver implements RelationshipResolver {

    private static final ResolverDescriptor DESCRIPTOR =
        new ResolverDescriptor("forward-only", Set.of(VIEWER), Set.of(), Set.of());

    @Override
    public ResolverDescriptor descriptor() {
      return DESCRIPTOR;
    }

    @Override
    public ForwardRelationshipBatchResponse resolveForward(
        ForwardRelationshipBatchRequest request) {
      return new ForwardRelationshipBatchResponse(
          request.requests().stream()
              .map(item -> new ForwardRelationshipResponse(item, List.of()))
              .toList());
    }
  }
}
