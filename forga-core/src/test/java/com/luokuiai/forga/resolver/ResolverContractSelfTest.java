package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.resolver.testkit.AttributeResolverContract;
import com.luokuiai.forga.resolver.testkit.ForwardRelationshipResolverContract;
import com.luokuiai.forga.resolver.testkit.ReverseRelationshipResolverContract;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;

class ResolverContractSelfTest {

  private static final RelationRef VIEWER = new RelationRef("viewer");

  private static final AttributeRef STATUS = new AttributeRef("status");

  private static final ObjectRef DOCUMENT = new ObjectRef("document", "doc-1");

  private static final DirectSubject PRINCIPAL =
      new DirectSubject(new SubjectRef("principal", "subject-1"));

  private final SampleResolver resolver = new SampleResolver();

  @Nested
  final class ForwardContract extends ForwardRelationshipResolverContract {

    @Override
    protected ForwardRelationshipResolver resolver() {
      return resolver;
    }

    @Override
    protected ForwardRelationshipBatchRequest forwardBatch() {
      return new ForwardRelationshipBatchRequest(
          List.of(new ForwardRelationshipRequest(DOCUMENT, VIEWER, 10)));
    }
  }

  @Nested
  final class ReverseContract extends ReverseRelationshipResolverContract {

    @Override
    protected ReverseRelationshipResolver resolver() {
      return resolver;
    }

    @Override
    protected ReverseRelationshipBatchRequest reverseBatch() {
      return new ReverseRelationshipBatchRequest(
          List.of(new ReverseRelationshipRequest("document", VIEWER, PRINCIPAL, 10)));
    }
  }

  @Nested
  final class AttributeContract extends AttributeResolverContract {

    @Override
    protected AttributeResolver resolver() {
      return resolver;
    }

    @Override
    protected AttributeResolutionBatchRequest attributeBatch() {
      return new AttributeResolutionBatchRequest(
          List.of(new AttributeResolutionRequest(DOCUMENT, List.of(STATUS))));
    }
  }

  private static final class SampleResolver
      implements ForwardRelationshipResolver, ReverseRelationshipResolver, AttributeResolver {

    @Override
    public String name() {
      return "sample";
    }

    @Override
    public Set<RelationRef> forwardRelations() {
      return Set.of(VIEWER);
    }

    @Override
    public Set<RelationRef> reverseRelations() {
      return Set.of(VIEWER);
    }

    @Override
    public Set<AttributeRef> attributes() {
      return Set.of(STATUS);
    }

    @Override
    public ForwardRelationshipBatchResponse resolveForward(
        ForwardRelationshipBatchRequest request) {
      return new ForwardRelationshipBatchResponse(
          request.requests().stream()
              .map(item -> new ForwardRelationshipResponse(item, List.of(PRINCIPAL)))
              .toList());
    }

    @Override
    public ReverseRelationshipBatchResponse resolveReverse(
        ReverseRelationshipBatchRequest request) {
      return new ReverseRelationshipBatchResponse(
          request.requests().stream()
              .map(item -> new ReverseRelationshipResponse(item, List.of(DOCUMENT)))
              .toList());
    }

    @Override
    public AttributeResolutionBatchResponse resolveAttributes(
        AttributeResolutionBatchRequest request) {
      return new AttributeResolutionBatchResponse(
          request.requests().stream()
              .map(
                  item ->
                      new AttributeResolutionResponse(
                          item, List.of(new ResolvedAttribute(STATUS, "active"))))
              .toList());
    }
  }
}
