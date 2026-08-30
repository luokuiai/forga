package com.luokuiai.forga.core.eval;

import java.util.List;

/**
 * Batch lookup for host-owned effective permission grants.
 *
 * <p>Implementations must treat each submitted list as one lookup unit. They should read one
 * request-consistent snapshot and use a fixed number of set-oriented repository queries instead of
 * querying once per request.
 */
@FunctionalInterface
public interface PermissionGrantLookup {

  /**
   * Resolves a bounded batch of complete authorization requests.
   *
   * <p>The returned map must be assembled for the complete batch. Implementations should extract
   * subject, object, and permission keys, load matching grants with batched {@code IN} or join
   * queries, and map the results in memory. Looping over requests and calling a single-row
   * repository method can produce N+1 queries and violates the intended integration contract.
   *
   * @param requests distinct check requests
   * @param context current evaluation read context
   * @return complete consistency-aware batch
   */
  BatchResolution<CheckRequest, Boolean> resolve(
      List<CheckRequest> requests, EvaluationReadContext context);

  /**
   * Returns a lookup that denies every dynamic grant.
   *
   * @return deny-all lookup
   */
  static PermissionGrantLookup denyAll() {
    return (requests, context) ->
        new BatchResolution<>(
            requests.stream()
                .distinct()
                .collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                        request -> request, request -> false)),
            context.consistency());
  }
}
