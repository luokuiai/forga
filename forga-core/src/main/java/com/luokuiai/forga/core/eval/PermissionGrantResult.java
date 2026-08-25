package com.luokuiai.forga.core.eval;

import com.luokuiai.forga.core.model.ConsistencyToken;
import java.util.Optional;

/**
 * Result of resolving one host-owned effective permission grant.
 *
 * @param granted whether the requested grant exists
 * @param consistency optional host consistency marker
 */
public record PermissionGrantResult(
    boolean granted, Optional<ConsistencyToken> consistency) {

  /**
   * Creates a result without a consistency marker.
   *
   * @param granted whether the requested grant exists
   */
  public PermissionGrantResult(boolean granted) {
    this(granted, Optional.empty());
  }

  /** Creates a permission grant result. */
  public PermissionGrantResult {
    consistency = consistency == null ? Optional.empty() : consistency;
  }
}
