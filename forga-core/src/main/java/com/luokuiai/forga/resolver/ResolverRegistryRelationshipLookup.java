package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.eval.RelationLookupRequest;
import com.luokuiai.forga.core.eval.RelationshipEntry;
import com.luokuiai.forga.core.eval.RelationshipLookup;
import com.luokuiai.forga.core.eval.RelationshipLookupException;
import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Adapts registered forward relationship resolvers to evaluator lookups. */
public final class ResolverRegistryRelationshipLookup implements RelationshipLookup {

  private final ResolverRegistry resolvers;

  /**
   * Creates a forward relationship lookup.
   *
   * @param resolvers resolver registry
   */
  public ResolverRegistryRelationshipLookup(ResolverRegistry resolvers) {
    this.resolvers = Objects.requireNonNull(resolvers, "resolvers are required");
  }

  @Override
  public Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
      List<RelationLookupRequest> requests) {
    return resolve(requests, Optional.empty());
  }

  @Override
  public Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
      List<RelationLookupRequest> requests, Optional<Instant> deadline) {
    List<RelationLookupRequest> unique = uniqueRequests(requests);
    if (unique.isEmpty()) {
      return Map.of();
    }
    Map<RelationshipResolver, List<RelationLookupRequest>> grouped = new LinkedHashMap<>();
    for (RelationLookupRequest request : unique) {
      RelationshipResolver resolver =
          resolvers
              .findForward(request.relation())
              .orElseThrow(
                  () ->
                      ResolverLookupSupport.failure(
                          "missing forward resolver for relation: " + request.relation().name()));
      grouped.computeIfAbsent(resolver, ignored -> new ArrayList<>()).add(request);
    }

    Map<RelationLookupRequest, List<RelationshipEntry>> resolved = new LinkedHashMap<>();
    grouped.forEach(
        (resolver, groupedRequests) ->
            ResolverLookupSupport.batches(groupedRequests)
                .forEach(batch -> resolveBatch(resolver, batch, deadline, resolved)));
    return Map.copyOf(resolved);
  }

  private static List<RelationLookupRequest> uniqueRequests(List<RelationLookupRequest> requests) {
    Objects.requireNonNull(requests, "requests are required");
    return List.copyOf(new LinkedHashSet<>(List.copyOf(requests)));
  }

  private static void resolveBatch(
      RelationshipResolver resolver,
      List<RelationLookupRequest> requests,
      Optional<Instant> deadline,
      Map<RelationLookupRequest, List<RelationshipEntry>> resolved) {
    Map<ForwardRelationshipRequest, RelationLookupRequest> submitted = new LinkedHashMap<>();
    for (RelationLookupRequest request : requests) {
      ForwardRelationshipRequest resolverRequest =
          new ForwardRelationshipRequest(
              request.object(),
              request.relation(),
              ResolverBounds.MAX_LIMIT,
              new ResolverContext(
                  ConsistencyContext.empty(), deadline.map(ResolverDeadline::new)));
      submitted.put(resolverRequest, request);
    }

    ForwardRelationshipBatchResponse batchResponse;
    try {
      batchResponse =
          resolver.resolveForward(
              new ForwardRelationshipBatchRequest(List.copyOf(submitted.keySet())));
    } catch (RelationshipLookupException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw ResolverLookupSupport.failure(
          "forward resolver failed: " + resolver.descriptor().name());
    }
    if (batchResponse == null || batchResponse.responses().size() != submitted.size()) {
      throw ResolverLookupSupport.failure(
          "forward resolver returned an incomplete batch: " + resolver.descriptor().name());
    }
    for (ForwardRelationshipResponse response : batchResponse.responses()) {
      RelationLookupRequest request = submitted.remove(response.request());
      if (request == null) {
        throw ResolverLookupSupport.failure(
            "forward resolver returned an unexpected response: " + resolver.descriptor().name());
      }
      resolved.put(request, entries(response.subjects()));
    }
    if (!submitted.isEmpty()) {
      throw ResolverLookupSupport.failure(
          "forward resolver omitted a response: " + resolver.descriptor().name());
    }
  }

  private static List<RelationshipEntry> entries(List<RelationshipSubject> subjects) {
    return subjects.stream()
        .map(
            subject -> {
              if (subject instanceof DirectSubject direct) {
                return RelationshipEntry.subject(direct.subject());
              }
              if (subject instanceof SubjectSetSubject subjectSet) {
                return RelationshipEntry.subjectSet(subjectSet.subjectSet());
              }
              throw ResolverLookupSupport.failure("forward resolver returned an unknown subject");
            })
        .toList();
  }
}
