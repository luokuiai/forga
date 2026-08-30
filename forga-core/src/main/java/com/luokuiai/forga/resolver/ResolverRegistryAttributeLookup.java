package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.eval.AttributeLookup;
import com.luokuiai.forga.core.eval.AttributeLookupRequest;
import com.luokuiai.forga.core.eval.BatchResolution;
import com.luokuiai.forga.core.eval.DecisionReason;
import com.luokuiai.forga.core.eval.EvaluationReadContext;
import com.luokuiai.forga.core.eval.RelationshipLookupException;
import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ConsistencyToken;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Adapts registered attribute resolvers to evaluator lookups. */
public final class ResolverRegistryAttributeLookup implements AttributeLookup {

  private final ResolverRegistry resolvers;

  /**
   * Creates an attribute lookup.
   *
   * @param resolvers resolver registry
   */
  public ResolverRegistryAttributeLookup(ResolverRegistry resolvers) {
    this.resolvers = Objects.requireNonNull(resolvers, "resolvers are required");
  }

  @Override
  public BatchResolution<AttributeLookupRequest, Map<AttributeRef, String>> resolve(
      List<AttributeLookupRequest> requests, EvaluationReadContext context) {
    Objects.requireNonNull(context, "context is required");
    List<AttributeLookupRequest> unique = uniqueRequests(requests);
    if (unique.isEmpty()) {
      return new BatchResolution<>(Map.of(), context.consistency());
    }
    Map<AttributeLookupRequest, Map<AttributeRef, String>> resolved = new LinkedHashMap<>();
    unique.forEach(request -> resolved.put(request, new LinkedHashMap<>()));
    Map<AttributeResolver, Map<AttributeLookupRequest, Set<AttributeRef>>> grouped =
        groupByResolver(unique);

    Optional<ConsistencyToken> consistency = context.consistency();
    for (Map.Entry<AttributeResolver, Map<AttributeLookupRequest, Set<AttributeRef>>> group
        : grouped.entrySet()) {
      List<AttributePortion> portions =
          group.getValue().entrySet().stream()
              .map(entry -> new AttributePortion(entry.getKey(), entry.getValue()))
              .toList();
      for (List<AttributePortion> batch : ResolverLookupSupport.batches(portions)) {
        consistency =
            resolveBatch(
                group.getKey(),
                batch,
                new EvaluationReadContext(consistency, context.deadline()),
                resolved);
      }
    }
    Map<AttributeLookupRequest, Map<AttributeRef, String>> immutable = new LinkedHashMap<>();
    resolved.forEach((request, values) -> immutable.put(request, Map.copyOf(values)));
    return new BatchResolution<>(immutable, consistency);
  }

  private Map<AttributeResolver, Map<AttributeLookupRequest, Set<AttributeRef>>> groupByResolver(
      List<AttributeLookupRequest> requests) {
    Map<AttributeResolver, Map<AttributeLookupRequest, Set<AttributeRef>>> grouped =
        new LinkedHashMap<>();
    for (AttributeLookupRequest request : requests) {
      for (AttributeRef attribute : request.attributes()) {
        AttributeResolver resolver =
            resolvers
                .findAttribute(attribute)
                .orElseThrow(
                    () ->
                        ResolverLookupSupport.failure(
                            "missing attribute resolver for: " + attribute.name()));
        grouped
            .computeIfAbsent(resolver, ignored -> new LinkedHashMap<>())
            .computeIfAbsent(request, ignored -> new LinkedHashSet<>())
            .add(attribute);
      }
    }
    return grouped;
  }

  private static Optional<ConsistencyToken> resolveBatch(
      AttributeResolver resolver,
      List<AttributePortion> portions,
      EvaluationReadContext context,
      Map<AttributeLookupRequest, Map<AttributeRef, String>> resolved) {
    Map<AttributeResolutionRequest, List<AttributeLookupRequest>> submitted =
        new LinkedHashMap<>();
    for (AttributePortion portion : portions) {
      AttributeResolutionRequest resolverRequest =
          new AttributeResolutionRequest(
              portion.request().object(),
              portion.attributes().stream()
                  .sorted(Comparator.comparing(AttributeRef::name))
                  .toList(),
              new ResolverContext(
                  new ConsistencyContext(context.consistency()),
                  context.deadline().map(ResolverDeadline::new)));
      submitted
          .computeIfAbsent(resolverRequest, ignored -> new ArrayList<>())
          .add(portion.request());
    }

    AttributeResolutionBatchResponse batchResponse;
    try {
      batchResponse =
          resolver.resolveAttributes(
              new AttributeResolutionBatchRequest(List.copyOf(submitted.keySet())));
    } catch (RelationshipLookupException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw ResolverLookupSupport.failure("attribute resolver failed: " + resolver.name());
    }
    if (batchResponse == null || batchResponse.responses().size() != submitted.size()) {
      throw ResolverLookupSupport.failure(
          "attribute resolver returned an incomplete batch: " + resolver.name());
    }
    Optional<ConsistencyToken> consistency = context.consistency();
    for (AttributeResolutionResponse response : batchResponse.responses()) {
      if (response == null) {
        throw ResolverLookupSupport.failure(
            "attribute resolver returned a null response: " + resolver.name());
      }
      List<AttributeLookupRequest> requests = submitted.remove(response.request());
      if (requests == null) {
        throw ResolverLookupSupport.failure(
            "attribute resolver returned an unexpected response: " + resolver.name());
      }
      consistency = acceptConsistency(consistency, response.consistency().token());
      for (AttributeLookupRequest request : requests) {
        merge(response, request, resolved.get(request), resolver.name());
      }
    }
    if (!submitted.isEmpty()) {
      throw ResolverLookupSupport.failure(
          "attribute resolver omitted a response: " + resolver.name());
    }
    return consistency;
  }

  private static void merge(
      AttributeResolutionResponse response,
      AttributeLookupRequest request,
      Map<AttributeRef, String> resolved,
      String resolverName) {
    Set<AttributeRef> returned = new LinkedHashSet<>();
    for (ResolvedAttribute attribute : response.attributes()) {
      if (!response.request().attributes().contains(attribute.attribute())
          || !request.attributes().contains(attribute.attribute())) {
        throw ResolverLookupSupport.failure(
            "attribute resolver returned an unexpected attribute: " + resolverName);
      }
      if (!returned.add(attribute.attribute())
          || resolved.putIfAbsent(attribute.attribute(), attribute.value()) != null) {
        throw ResolverLookupSupport.failure(
            "attribute resolver returned a duplicate attribute: " + resolverName);
      }
    }
  }

  private static Optional<ConsistencyToken> acceptConsistency(
      Optional<ConsistencyToken> established, Optional<ConsistencyToken> returned) {
    if (returned.isEmpty()) {
      return established;
    }
    if (established.isEmpty()) {
      return returned;
    }
    if (!established.equals(returned)) {
      throw new RelationshipLookupException(
          DecisionReason.CONSISTENCY_CONFLICT,
          "attribute resolver returned a conflicting consistency token");
    }
    return established;
  }

  private static List<AttributeLookupRequest> uniqueRequests(
      List<AttributeLookupRequest> requests) {
    Objects.requireNonNull(requests, "requests are required");
    return List.copyOf(new LinkedHashSet<>(List.copyOf(requests)));
  }

  private record AttributePortion(
      AttributeLookupRequest request, Set<AttributeRef> attributes) {

    private AttributePortion {
      request = Objects.requireNonNull(request, "request is required");
      attributes = Set.copyOf(attributes);
    }
  }
}
