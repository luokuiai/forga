package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.model.RelationRef;
import java.util.Set;

/** Resolves bounded reverse relationship batches owned by a host. */
public interface ReverseRelationshipResolver extends Resolver {

  /**
   * Returns reverse relations owned by this resolver.
   *
   * @return immutable relation capabilities
   */
  Set<RelationRef> reverseRelations();

  /**
   * Resolves one bounded reverse relationship batch.
   *
   * @param request batch request
   * @return complete batch response
   */
  ReverseRelationshipBatchResponse resolveReverse(ReverseRelationshipBatchRequest request);
}
