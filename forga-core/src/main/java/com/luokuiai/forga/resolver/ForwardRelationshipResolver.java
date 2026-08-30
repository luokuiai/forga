package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.model.RelationRef;
import java.util.Set;

/** Resolves bounded forward relationship batches owned by a host. */
public interface ForwardRelationshipResolver extends Resolver {

  /**
   * Returns forward relations owned by this resolver.
   *
   * @return immutable relation capabilities
   */
  Set<RelationRef> forwardRelations();

  /**
   * Resolves one bounded forward relationship batch.
   *
   * @param request batch request
   * @return complete batch response
   */
  ForwardRelationshipBatchResponse resolveForward(ForwardRelationshipBatchRequest request);
}
