package com.luokuiai.forga.core.eval;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.Optional;

/**
 * Batch relationship lookup used by the evaluator.
 */
public interface RelationshipLookup {

  /**
   * Resolves a bounded batch of relation lookup requests.
   *
   * @param requests immutable lookup requests
   * @return entries keyed by request
   */
  Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
      List<RelationLookupRequest> requests);

  /**
   * Resolves a bounded batch with an optional evaluation deadline.
   *
   * @param requests immutable lookup requests
   * @param deadline optional absolute evaluation deadline
   * @return entries keyed by request
   */
  default Map<RelationLookupRequest, List<RelationshipEntry>> resolve(
      List<RelationLookupRequest> requests, Optional<Instant> deadline) {
    return resolve(requests);
  }
}
