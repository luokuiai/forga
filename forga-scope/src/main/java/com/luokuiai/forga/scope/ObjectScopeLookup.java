package com.luokuiai.forga.scope;

import com.luokuiai.forga.core.model.ObjectRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves host-owned authorization scopes for a bounded object batch. */
@FunctionalInterface
public interface ObjectScopeLookup {

  /**
   * Resolves one complete batch of object ownership results.
   *
   * <p>The returned map must contain exactly one non-null {@link Optional} for every distinct
   * submitted object. Implementations should use a set-oriented repository query and participate in
   * the host request's transaction or equivalent snapshot boundary.
   *
   * @param objects distinct protected objects
   * @return complete ownership results
   */
  Map<ObjectRef, Optional<ScopeRef>> resolve(List<ObjectRef> objects);
}
