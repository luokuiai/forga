package com.luokuiai.forga.core.eval;

import com.luokuiai.forga.core.model.AttributeRef;
import java.util.List;
import java.util.Map;

/** Batch object attribute lookup used by the evaluator. */
@FunctionalInterface
public interface AttributeLookup {

  /**
   * Resolves a bounded batch of object attribute requests.
   *
   * @param requests immutable lookup requests
   * @param context current evaluation read context
   * @return complete consistency-aware batch
   */
  BatchResolution<AttributeLookupRequest, Map<AttributeRef, String>> resolve(
      List<AttributeLookupRequest> requests, EvaluationReadContext context);

  /**
   * Returns an unavailable lookup that fails closed when used.
   *
   * @return unavailable lookup
   */
  static AttributeLookup unavailable() {
    return (requests, context) -> {
      throw new RelationshipLookupException(
          DecisionReason.RESOLVER_FAILURE, "no attribute lookup is registered");
    };
  }
}
