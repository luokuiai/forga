package com.luokuiai.forga.core.eval;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.Optional;

/**
 * Batch-capable reverse relationship lookup used by object listing.
 */
@FunctionalInterface
public interface ObjectListingLookup {

  /**
   * Resolves reverse relationship requests.
   *
   * @param requests reverse lookup requests
   * @return pages keyed by request
   */
  Map<ReverseRelationLookupRequest, ObjectListingPage> resolve(
      List<ReverseRelationLookupRequest> requests);

  /**
   * Resolves reverse requests with an optional evaluation deadline.
   *
   * @param requests reverse lookup requests
   * @param deadline optional absolute evaluation deadline
   * @return pages keyed by request
   */
  default Map<ReverseRelationLookupRequest, ObjectListingPage> resolve(
      List<ReverseRelationLookupRequest> requests, Optional<Instant> deadline) {
    return resolve(requests);
  }
}
