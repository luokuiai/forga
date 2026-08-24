package com.luokuiai.forga.core.eval;

import java.time.Duration;
import java.util.Optional;

/**
 * Bounds applied during authorization evaluation.
 *
 * @param maxDepth maximum recursive expression depth
 * @param maxResolverCalls maximum relation lookup calls
 * @param maxVisitedNodes maximum expression or relation nodes visited
 * @param maxIntermediateResults maximum relationship entries processed per lookup
 * @param maxBatchSize maximum number of checks in one batch
 * @param timeout optional timeout applied independently to each evaluation
 */
public record EvaluationLimits(
    int maxDepth,
    int maxResolverCalls,
    int maxVisitedNodes,
    int maxIntermediateResults,
    int maxBatchSize,
    Optional<Duration> timeout) {

  /**
   * Creates evaluation limits without node, intermediate, batch, or timeout overrides.
   *
   * @param maxDepth maximum recursive expression depth
   * @param maxResolverCalls maximum relation lookup calls
   */
  public EvaluationLimits(int maxDepth, int maxResolverCalls) {
    this(maxDepth, maxResolverCalls, 10_000, 10_000, 500, Optional.empty());
  }

  /**
   * Creates evaluation limits.
   *
   * @param maxDepth maximum recursive expression depth
   * @param maxResolverCalls maximum relation lookup calls
   * @param maxVisitedNodes maximum expression or relation nodes visited
   * @param maxIntermediateResults maximum relationship entries processed per lookup
   * @param maxBatchSize maximum number of checks in one batch
   * @param timeout optional timeout applied independently to each evaluation
   */
  public EvaluationLimits {
    if (maxDepth < 1) {
      throw new IllegalArgumentException("maxDepth must be positive");
    }
    if (maxResolverCalls < 1) {
      throw new IllegalArgumentException("maxResolverCalls must be positive");
    }
    if (maxVisitedNodes < 1) {
      throw new IllegalArgumentException("maxVisitedNodes must be positive");
    }
    if (maxIntermediateResults < 1) {
      throw new IllegalArgumentException("maxIntermediateResults must be positive");
    }
    if (maxBatchSize < 1) {
      throw new IllegalArgumentException("maxBatchSize must be positive");
    }
    timeout = timeout == null ? Optional.empty() : timeout;
    if (timeout.filter(Duration::isNegative).isPresent()) {
      throw new IllegalArgumentException("timeout must not be negative");
    }
  }

  /**
   * Returns conservative default limits.
   *
   * @return default limits
   */
  public static EvaluationLimits defaults() {
    return new EvaluationLimits(32, 1000);
  }
}
