package com.luokuiai.forga.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.RelationRef;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResolverRegistryTest {

  @Test
  void discoversResolversByDeclaredCapabilities() {
    StubResolver resolver =
        new StubResolver(
            new ResolverDescriptor(
                "main",
                Set.of(new RelationRef("viewer")),
                Set.of(new RelationRef("owner")),
                Set.of(new AttributeRef("status"))));

    ResolverRegistry registry = new ResolverRegistry(List.of(resolver));

    assertThat(registry.findForward(new RelationRef("viewer"))).contains(resolver);
    assertThat(registry.findReverse(new RelationRef("owner"))).contains(resolver);
    assertThat(registry.findAttribute(new AttributeRef("status"))).contains(resolver);
    assertThat(registry.findForward(new RelationRef("missing"))).isEmpty();
  }

  @Test
  void rejectsDuplicateResolverNames() {
    StubResolver first = resolverNamed("main");
    StubResolver second = resolverNamed("main");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ResolverRegistry(List.of(first, second)))
        .withMessageContaining("duplicate resolver name");
  }

  @Test
  void rejectsDuplicateCapabilityOwnership() {
    RelationRef viewer = new RelationRef("viewer");
    AttributeRef status = new AttributeRef("status");
    StubResolver first =
        new StubResolver(
            new ResolverDescriptor(
                "first", Set.of(viewer), Set.of(viewer), Set.of(status)));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ResolverRegistry(
                    List.of(
                        first,
                        new StubResolver(
                            new ResolverDescriptor(
                                "second", Set.of(viewer), Set.of(), Set.of())))))
        .withMessageContainingAll("forward relation", "first", "second");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ResolverRegistry(
                    List.of(
                        first,
                        new StubResolver(
                            new ResolverDescriptor(
                                "second", Set.of(), Set.of(viewer), Set.of())))))
        .withMessageContainingAll("reverse relation", "first", "second");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ResolverRegistry(
                    List.of(
                        first,
                        new StubResolver(
                            new ResolverDescriptor(
                                "second", Set.of(), Set.of(), Set.of(status))))))
        .withMessageContainingAll("attribute", "first", "second");
  }

  @Test
  void copiesDescriptorCollections() {
    Set<RelationRef> forward = new java.util.HashSet<>(Set.of(new RelationRef("viewer")));

    ResolverDescriptor descriptor =
        new ResolverDescriptor("main", forward, Set.of(), Set.of());
    forward.clear();

    assertThat(descriptor.forwardRelations()).containsExactly(new RelationRef("viewer"));
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> descriptor.forwardRelations().clear());
  }

  private static StubResolver resolverNamed(String name) {
    return new StubResolver(new ResolverDescriptor(name, Set.of(), Set.of(), Set.of()));
  }

  private record StubResolver(ResolverDescriptor descriptor) implements RelationshipResolver {

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
