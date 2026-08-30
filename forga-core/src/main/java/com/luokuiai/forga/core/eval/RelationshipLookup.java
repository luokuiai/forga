package com.luokuiai.forga.core.eval;

import java.util.List;

/**
 * Batch relationship lookup used by the evaluator.
 */
@FunctionalInterface
public interface RelationshipLookup {

  /**
   * Resolves a bounded batch of relation lookup requests.
   *
   * @param requests immutable lookup requests
   * @param context current evaluation read context
   * @return complete consistency-aware batch
   */
  BatchResolution<RelationLookupRequest, List<RelationshipEntry>> resolve(
      List<RelationLookupRequest> requests, EvaluationReadContext context);
}
