package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.eval.BatchResolution;
import com.luokuiai.forga.core.eval.DecisionReason;
import com.luokuiai.forga.core.eval.EvaluationReadContext;
import com.luokuiai.forga.core.eval.RelationLookupRequest;
import com.luokuiai.forga.core.eval.RelationshipEntry;
import com.luokuiai.forga.core.eval.RelationshipLookup;
import com.luokuiai.forga.core.eval.RelationshipLookupException;
import com.luokuiai.forga.core.model.ConsistencyToken;
import java.util.ArrayList;
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
  public BatchResolution<RelationLookupRequest, List<RelationshipEntry>> resolve(
      List<RelationLookupRequest> requests, EvaluationReadContext context) {
    Objects.requireNonNull(context, "context is required");
    List<RelationLookupRequest> unique = uniqueRequests(requests);
    if (unique.isEmpty()) {
      return new BatchResolution<>(Map.of(), context.consistency());
    }
    Map<ForwardRelationshipResolver, List<RelationLookupRequest>> grouped =
        new LinkedHashMap<>();
    for (RelationLookupRequest request : unique) {
      ForwardRelationshipResolver resolver =
          resolvers
              .findForward(request.relation())
              .orElseThrow(
                  () ->
                      ResolverLookupSupport.failure(
                          "missing forward resolver for relation: " + request.relation().name()));
      grouped.computeIfAbsent(resolver, ignored -> new ArrayList<>()).add(request);
    }

    Map<RelationLookupRequest, List<RelationshipEntry>> resolved = new LinkedHashMap<>();
    Optional<ConsistencyToken> consistency = context.consistency();
    for (Map.Entry<ForwardRelationshipResolver, List<RelationLookupRequest>> group
        : grouped.entrySet()) {
      for (List<RelationLookupRequest> batch : ResolverLookupSupport.batches(group.getValue())) {
        consistency =
            resolveBatch(
                group.getKey(),
                batch,
                new EvaluationReadContext(consistency, context.deadline()),
                resolved);
      }
    }
    return new BatchResolution<>(resolved, consistency);
  }

  private static List<RelationLookupRequest> uniqueRequests(List<RelationLookupRequest> requests) {
    Objects.requireNonNull(requests, "requests are required");
    return List.copyOf(new LinkedHashSet<>(List.copyOf(requests)));
  }

  private static Optional<ConsistencyToken> resolveBatch(
      ForwardRelationshipResolver resolver,
      List<RelationLookupRequest> requests,
      EvaluationReadContext context,
      Map<RelationLookupRequest, List<RelationshipEntry>> resolved) {
    Map<ForwardRelationshipRequest, RelationLookupRequest> submitted = new LinkedHashMap<>();
    for (RelationLookupRequest request : requests) {
      ForwardRelationshipRequest resolverRequest =
          new ForwardRelationshipRequest(
              request.object(),
              request.relation(),
              ResolverBounds.MAX_LIMIT,
              new ResolverContext(
                  new ConsistencyContext(context.consistency()),
                  context.deadline().map(ResolverDeadline::new)));
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
          "forward resolver failed: " + resolver.name());
    }
    if (batchResponse == null || batchResponse.responses().size() != submitted.size()) {
      throw ResolverLookupSupport.failure(
          "forward resolver returned an incomplete batch: " + resolver.name());
    }
    Optional<ConsistencyToken> consistency = context.consistency();
    for (ForwardRelationshipResponse response : batchResponse.responses()) {
      RelationLookupRequest request = submitted.remove(response.request());
      if (request == null) {
        throw ResolverLookupSupport.failure(
            "forward resolver returned an unexpected response: " + resolver.name());
      }
      consistency = acceptConsistency(consistency, response.consistency().token());
      resolved.put(request, entries(response.subjects()));
    }
    if (!submitted.isEmpty()) {
      throw ResolverLookupSupport.failure(
          "forward resolver omitted a response: " + resolver.name());
    }
    return consistency;
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
          "forward resolver returned a conflicting consistency token");
    }
    return established;
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
