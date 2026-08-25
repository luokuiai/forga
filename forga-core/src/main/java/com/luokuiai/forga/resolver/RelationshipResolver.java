package com.luokuiai.forga.resolver;

import java.util.List;

/**
 * Resolver capable of answering relationship and attribute requests.
 *
 * <p>Implementations must treat every submitted batch as one lookup unit. Host data should be read
 * from one request-consistent snapshot with a fixed number of set-oriented queries. Querying a
 * repository once per item can produce N+1 queries and violates the intended resolver contract.
 */
public interface RelationshipResolver {

  /**
   * Returns declared resolver capabilities.
   *
   * @return resolver descriptor
   */
  ResolverDescriptor descriptor();

  /**
   * Resolves forward relationship requests.
   *
   * <p>Implementations should collect object and relation keys for the complete batch, resolve them
   * with batched {@code IN} or join queries, and assemble one response per submitted request in
   * memory.
   *
   * @param request batch request
   * @return batch response
   */
  ForwardRelationshipBatchResponse resolveForward(ForwardRelationshipBatchRequest request);

  /**
   * Resolves reverse relationship requests.
   *
   * <p>Declared reverse implementations should resolve the complete batch with set-oriented,
   * paginated queries rather than one query per submitted request.
   *
   * <p>The default returns one empty response per request for resolvers that declare no reverse
   * capabilities.
   *
   * @param request batch request
   * @return batch response
   */
  default ReverseRelationshipBatchResponse resolveReverse(ReverseRelationshipBatchRequest request) {
    return new ReverseRelationshipBatchResponse(
        request.requests().stream()
            .map(item -> new ReverseRelationshipResponse(item, List.of()))
            .toList());
  }

  /**
   * Resolves attribute requests.
   *
   * <p>Declared attribute implementations should load requested attributes for the complete batch
   * with set-oriented queries rather than one query per submitted request.
   *
   * <p>The default returns one empty response per request for resolvers that declare no attribute
   * capabilities.
   *
   * @param request batch request
   * @return batch response
   */
  default AttributeResolutionBatchResponse resolveAttributes(
      AttributeResolutionBatchRequest request) {
    return new AttributeResolutionBatchResponse(
        request.requests().stream()
            .map(item -> new AttributeResolutionResponse(item, List.of()))
            .toList());
  }
}
