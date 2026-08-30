package com.luokuiai.forga.core.eval;

import com.luokuiai.forga.core.model.ConsistencyToken;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete values and opaque consistency state returned by one bounded host lookup batch.
 *
 * @param values immutable values keyed by submitted request
 * @param consistency optional consistency token for the complete batch
 * @param <K> request key type
 * @param <V> resolved value type
 */
public record BatchResolution<K, V>(
    Map<K, V> values, Optional<ConsistencyToken> consistency) {

  /** Creates a batch resolution. */
  public BatchResolution {
    values = Map.copyOf(Objects.requireNonNull(values, "values are required"));
    consistency = consistency == null ? Optional.empty() : consistency;
  }

  /**
   * Creates an unversioned batch resolution.
   *
   * @param values values keyed by submitted request
   * @param <K> request key type
   * @param <V> resolved value type
   * @return unversioned batch resolution
   */
  public static <K, V> BatchResolution<K, V> unversioned(Map<K, V> values) {
    return new BatchResolution<>(values, Optional.empty());
  }
}
