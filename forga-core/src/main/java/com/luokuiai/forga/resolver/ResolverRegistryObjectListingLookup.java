package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.eval.DirectReverseLookupSubject;
import com.luokuiai.forga.core.eval.ListObjectsCursor;
import com.luokuiai.forga.core.eval.ObjectListingLookup;
import com.luokuiai.forga.core.eval.ObjectListingPage;
import com.luokuiai.forga.core.eval.RelationshipLookupException;
import com.luokuiai.forga.core.eval.ReverseLookupSubject;
import com.luokuiai.forga.core.eval.ReverseRelationLookupRequest;
import com.luokuiai.forga.core.eval.SubjectSetReverseLookupSubject;
import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Adapts registered reverse relationship resolvers to evaluator object listings. */
public final class ResolverRegistryObjectListingLookup implements ObjectListingLookup {

  private final ResolverRegistry resolvers;

  /**
   * Creates a reverse object listing lookup.
   *
   * @param resolvers resolver registry
   */
  public ResolverRegistryObjectListingLookup(ResolverRegistry resolvers) {
    this.resolvers = Objects.requireNonNull(resolvers, "resolvers are required");
  }

  @Override
  public Map<ReverseRelationLookupRequest, ObjectListingPage> resolve(
      List<ReverseRelationLookupRequest> requests) {
    return resolve(requests, Optional.empty());
  }

  @Override
  public Map<ReverseRelationLookupRequest, ObjectListingPage> resolve(
      List<ReverseRelationLookupRequest> requests, Optional<Instant> deadline) {
    List<ReverseRelationLookupRequest> unique = uniqueRequests(requests);
    if (unique.isEmpty()) {
      return Map.of();
    }
    Map<RelationshipResolver, List<ReverseRelationLookupRequest>> grouped = new LinkedHashMap<>();
    for (ReverseRelationLookupRequest request : unique) {
      RelationshipResolver resolver =
          resolvers
              .findReverse(request.relation())
              .orElseThrow(
                  () ->
                      ResolverLookupSupport.failure(
                          "missing reverse resolver for relation: " + request.relation().name()));
      grouped.computeIfAbsent(resolver, ignored -> new ArrayList<>()).add(request);
    }

    Map<ReverseRelationLookupRequest, ObjectListingPage> resolved = new LinkedHashMap<>();
    grouped.forEach(
        (resolver, groupedRequests) ->
            ResolverLookupSupport.batches(groupedRequests)
                .forEach(batch -> resolveBatch(resolver, batch, deadline, resolved)));
    return Map.copyOf(resolved);
  }

  private static List<ReverseRelationLookupRequest> uniqueRequests(
      List<ReverseRelationLookupRequest> requests) {
    Objects.requireNonNull(requests, "requests are required");
    return List.copyOf(new LinkedHashSet<>(List.copyOf(requests)));
  }

  private static void resolveBatch(
      RelationshipResolver resolver,
      List<ReverseRelationLookupRequest> requests,
      Optional<Instant> deadline,
      Map<ReverseRelationLookupRequest, ObjectListingPage> resolved) {
    Map<ReverseRelationshipRequest, ReverseRelationLookupRequest> submitted = new LinkedHashMap<>();
    for (ReverseRelationLookupRequest request : requests) {
      ReverseRelationshipRequest resolverRequest =
          new ReverseRelationshipRequest(
              request.objectType(),
              request.relation(),
              subject(request.subject()),
              request.cursor().map(cursor -> new PageCursor(cursor.token())),
              Math.min(request.limit(), ResolverBounds.MAX_LIMIT),
              new ResolverContext(
                  new ConsistencyContext(request.consistency()),
                  deadline.map(ResolverDeadline::new)));
      submitted.put(resolverRequest, request);
    }

    ReverseRelationshipBatchResponse batchResponse;
    try {
      batchResponse =
          resolver.resolveReverse(
              new ReverseRelationshipBatchRequest(List.copyOf(submitted.keySet())));
    } catch (RelationshipLookupException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw ResolverLookupSupport.failure(
          "reverse resolver failed: " + resolver.descriptor().name());
    }
    if (batchResponse == null || batchResponse.responses().size() != submitted.size()) {
      throw ResolverLookupSupport.failure(
          "reverse resolver returned an incomplete batch: " + resolver.descriptor().name());
    }
    for (ReverseRelationshipResponse response : batchResponse.responses()) {
      ReverseRelationLookupRequest request = submitted.remove(response.request());
      if (request == null) {
        throw ResolverLookupSupport.failure(
            "reverse resolver returned an unexpected response: " + resolver.descriptor().name());
      }
      resolved.put(
          request,
          new ObjectListingPage(
              response.objects(),
              response.nextCursor().map(cursor -> new ListObjectsCursor(cursor.value())),
              response.consistency().token()));
    }
    if (!submitted.isEmpty()) {
      throw ResolverLookupSupport.failure(
          "reverse resolver omitted a response: " + resolver.descriptor().name());
    }
  }

  private static RelationshipSubject subject(ReverseLookupSubject subject) {
    if (subject instanceof DirectReverseLookupSubject direct) {
      return new DirectSubject(direct.subject());
    }
    if (subject instanceof SubjectSetReverseLookupSubject subjectSet) {
      return new SubjectSetSubject(subjectSet.subjectSet());
    }
    throw ResolverLookupSupport.failure("reverse lookup contains an unknown subject");
  }
}
