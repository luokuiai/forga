package com.luokuiai.forga.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.luokuiai.forga.core.eval.AttributeLookupRequest;
import com.luokuiai.forga.core.eval.DecisionReason;
import com.luokuiai.forga.core.eval.EvaluationReadContext;
import com.luokuiai.forga.core.eval.RelationshipLookupException;
import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ConsistencyToken;
import com.luokuiai.forga.core.model.ObjectRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ResolverRegistryAttributeLookupTest {

  private static final ObjectRef DOCUMENT = new ObjectRef("document", "one");

  private static final AttributeRef STATUS = new AttributeRef("status");

  private static final AttributeRef CLASSIFICATION = new AttributeRef("classification");

  @Test
  void groupsAttributesByOwnerAndMergesOneLogicalResult() {
    Instant deadline = Instant.now().plusSeconds(5);
    ConsistencyToken token = new ConsistencyToken("revision-3");
    AtomicReference<AttributeResolutionRequest> statusRequest = new AtomicReference<>();
    AtomicReference<AttributeResolutionRequest> classificationRequest = new AtomicReference<>();
    TestAttributeResolver statusResolver =
        new TestAttributeResolver(
            "status",
            Set.of(STATUS),
            batch -> {
              statusRequest.set(batch.requests().get(0));
              return new AttributeResolutionBatchResponse(
                  batch.requests().stream()
                      .map(
                          request ->
                              new AttributeResolutionResponse(
                                  request,
                                  List.of(new ResolvedAttribute(STATUS, "active")),
                                  ConsistencyContext.of(token)))
                      .toList());
            });
    TestAttributeResolver classificationResolver =
        new TestAttributeResolver(
            "classification",
            Set.of(CLASSIFICATION),
            batch -> {
              classificationRequest.set(batch.requests().get(0));
              return new AttributeResolutionBatchResponse(
                  batch.requests().stream()
                      .map(
                          request ->
                              new AttributeResolutionResponse(
                                  request,
                                  List.of(new ResolvedAttribute(CLASSIFICATION, "internal")),
                                  ConsistencyContext.of(token)))
                      .toList());
            });
    ResolverRegistryAttributeLookup lookup =
        new ResolverRegistryAttributeLookup(
            new ResolverRegistry(List.of(statusResolver, classificationResolver)));
    AttributeLookupRequest request =
        new AttributeLookupRequest(DOCUMENT, Set.of(STATUS, CLASSIFICATION));

    var result =
        lookup.resolve(
            List.of(request),
            new EvaluationReadContext(Optional.empty(), Optional.of(deadline)));

    assertThat(result.values().get(request))
        .containsOnlyKeys(STATUS, CLASSIFICATION)
        .containsEntry(STATUS, "active")
        .containsEntry(CLASSIFICATION, "internal");
    assertThat(result.consistency()).contains(token);
    assertThat(
            List.of(
                statusRequest.get().context().consistency().token(),
                classificationRequest.get().context().consistency().token()))
        .containsExactlyInAnyOrder(Optional.empty(), Optional.of(token));
    assertThat(
            List.of(
                statusRequest.get().context().deadline(),
                classificationRequest.get().context().deadline()))
        .containsOnly(Optional.of(new ResolverDeadline(deadline)));
  }

  @Test
  void absentRequestedValueReturnsCompleteEmptyAttributeMap() {
    TestAttributeResolver resolver =
        new TestAttributeResolver(
            "status",
            Set.of(STATUS),
            batch ->
                new AttributeResolutionBatchResponse(
                    batch.requests().stream()
                        .map(request -> new AttributeResolutionResponse(request, List.of()))
                        .toList()));
    ResolverRegistryAttributeLookup lookup =
        new ResolverRegistryAttributeLookup(new ResolverRegistry(List.of(resolver)));
    AttributeLookupRequest request = new AttributeLookupRequest(DOCUMENT, Set.of(STATUS));

    var result = lookup.resolve(List.of(request), emptyContext());

    assertThat(result.values()).containsEntry(request, Map.of());
  }

  @Test
  void missingAttributeOwnerFailsClosed() {
    ResolverRegistryAttributeLookup lookup =
        new ResolverRegistryAttributeLookup(new ResolverRegistry(List.of()));
    AttributeLookupRequest request = new AttributeLookupRequest(DOCUMENT, Set.of(STATUS));

    assertThatExceptionOfType(RelationshipLookupException.class)
        .isThrownBy(() -> lookup.resolve(List.of(request), emptyContext()))
        .satisfies(
            exception -> assertThat(exception.reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE))
        .withMessageContaining("missing attribute resolver");
  }

  @Test
  void unexpectedResolvedAttributeFailsClosed() {
    TestAttributeResolver resolver =
        new TestAttributeResolver(
            "status",
            Set.of(STATUS),
            batch ->
                new AttributeResolutionBatchResponse(
                    batch.requests().stream()
                        .map(
                            request ->
                                new AttributeResolutionResponse(
                                    request,
                                    List.of(
                                        new ResolvedAttribute(
                                            CLASSIFICATION, "internal"))))
                        .toList()));
    ResolverRegistryAttributeLookup lookup =
        new ResolverRegistryAttributeLookup(new ResolverRegistry(List.of(resolver)));
    AttributeLookupRequest request = new AttributeLookupRequest(DOCUMENT, Set.of(STATUS));

    assertThatExceptionOfType(RelationshipLookupException.class)
        .isThrownBy(() -> lookup.resolve(List.of(request), emptyContext()))
        .satisfies(
            exception -> assertThat(exception.reason()).isEqualTo(DecisionReason.RESOLVER_FAILURE))
        .withMessageContaining("unexpected attribute");
  }

  private static EvaluationReadContext emptyContext() {
    return new EvaluationReadContext(Optional.empty(), Optional.empty());
  }

  private static final class TestAttributeResolver implements AttributeResolver {

    private final String name;

    private final Set<AttributeRef> attributes;

    private final Function<AttributeResolutionBatchRequest, AttributeResolutionBatchResponse>
        resolution;

    private TestAttributeResolver(
        String name,
        Set<AttributeRef> attributes,
        Function<AttributeResolutionBatchRequest, AttributeResolutionBatchResponse> resolution) {
      this.name = name;
      this.attributes = Set.copyOf(attributes);
      this.resolution = resolution;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Set<AttributeRef> attributes() {
      return attributes;
    }

    @Override
    public AttributeResolutionBatchResponse resolveAttributes(
        AttributeResolutionBatchRequest request) {
      return resolution.apply(request);
    }
  }
}
