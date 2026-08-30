package com.luokuiai.forga.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.RelationRef;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResolverRegistryTest {

  @Test
  void discoversResolversByImplementedCapabilities() {
    CompositeResolver resolver =
        new CompositeResolver(
            "main",
            Set.of(new RelationRef("viewer")),
            Set.of(new RelationRef("owner")),
            Set.of(new AttributeRef("status")));

    ResolverRegistry registry = new ResolverRegistry(List.of(resolver));

    assertThat(registry.findForward(new RelationRef("viewer"))).contains(resolver);
    assertThat(registry.findReverse(new RelationRef("owner"))).contains(resolver);
    assertThat(registry.findAttribute(new AttributeRef("status"))).contains(resolver);
    assertThat(registry.findForward(new RelationRef("missing"))).isEmpty();
  }

  @Test
  void registersSingleCapabilityResolverWithoutUnrelatedOperations() {
    AttributeOnlyResolver resolver =
        new AttributeOnlyResolver("attributes", Set.of(new AttributeRef("status")));

    ResolverRegistry registry = new ResolverRegistry(List.of(resolver));

    assertThat(registry.findAttribute(new AttributeRef("status"))).contains(resolver);
    assertThat(registry.findForward(new RelationRef("status"))).isEmpty();
    assertThat(registry.findReverse(new RelationRef("status"))).isEmpty();
  }

  @Test
  void rejectsDuplicateResolverNames() {
    Resolver first = () -> "main";
    Resolver second = () -> "main";

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ResolverRegistry(List.of(first, second)))
        .withMessageContaining("duplicate resolver name");
  }

  @Test
  void rejectsDuplicateCapabilityOwnership() {
    RelationRef viewer = new RelationRef("viewer");
    AttributeRef status = new AttributeRef("status");

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ResolverRegistry(
                    List.of(
                        new ForwardOnlyResolver("first", Set.of(viewer)),
                        new ForwardOnlyResolver("second", Set.of(viewer)))))
        .withMessageContainingAll("forward relation", "first", "second");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ResolverRegistry(
                    List.of(
                        new ReverseOnlyResolver("first", Set.of(viewer)),
                        new ReverseOnlyResolver("second", Set.of(viewer)))))
        .withMessageContainingAll("reverse relation", "first", "second");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ResolverRegistry(
                    List.of(
                        new AttributeOnlyResolver("first", Set.of(status)),
                        new AttributeOnlyResolver("second", Set.of(status)))))
        .withMessageContainingAll("attribute", "first", "second");
  }

  @Test
  void copiesCapabilityCollectionsAtRegistration() {
    Set<RelationRef> forward = new HashSet<>(Set.of(new RelationRef("viewer")));
    ForwardOnlyResolver resolver = new ForwardOnlyResolver("main", forward);

    ResolverRegistry registry = new ResolverRegistry(List.of(resolver));
    forward.clear();

    assertThat(registry.findForward(new RelationRef("viewer"))).contains(resolver);
  }

  private record ForwardOnlyResolver(String name, Set<RelationRef> forwardRelations)
      implements ForwardRelationshipResolver {

    @Override
    public ForwardRelationshipBatchResponse resolveForward(
        ForwardRelationshipBatchRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private record ReverseOnlyResolver(String name, Set<RelationRef> reverseRelations)
      implements ReverseRelationshipResolver {

    @Override
    public ReverseRelationshipBatchResponse resolveReverse(
        ReverseRelationshipBatchRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private record AttributeOnlyResolver(String name, Set<AttributeRef> attributes)
      implements AttributeResolver {

    @Override
    public AttributeResolutionBatchResponse resolveAttributes(
        AttributeResolutionBatchRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private record CompositeResolver(
      String name,
      Set<RelationRef> forwardRelations,
      Set<RelationRef> reverseRelations,
      Set<AttributeRef> attributes)
      implements ForwardRelationshipResolver, ReverseRelationshipResolver, AttributeResolver {

    @Override
    public ForwardRelationshipBatchResponse resolveForward(
        ForwardRelationshipBatchRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ReverseRelationshipBatchResponse resolveReverse(
        ReverseRelationshipBatchRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AttributeResolutionBatchResponse resolveAttributes(
        AttributeResolutionBatchRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
