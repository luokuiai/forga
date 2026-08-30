package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.model.AttributeRef;
import java.util.Set;

/** Resolves bounded object attribute batches owned by a host. */
public interface AttributeResolver extends Resolver {

  /**
   * Returns object attributes owned by this resolver.
   *
   * @return immutable attribute capabilities
   */
  Set<AttributeRef> attributes();

  /**
   * Resolves one bounded object attribute batch.
   *
   * @param request batch request
   * @return complete batch response
   */
  AttributeResolutionBatchResponse resolveAttributes(AttributeResolutionBatchRequest request);
}
