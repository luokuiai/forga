package com.luokuiai.forga.core.eval;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ObjectRef;
import java.util.Objects;
import java.util.Set;

/**
 * Request for resolver-owned attributes of one object.
 *
 * @param object object whose attributes are required
 * @param attributes required attributes
 */
public record AttributeLookupRequest(ObjectRef object, Set<AttributeRef> attributes) {

  /** Creates an attribute lookup request. */
  public AttributeLookupRequest {
    object = Objects.requireNonNull(object, "object is required");
    attributes = Set.copyOf(attributes);
    if (attributes.isEmpty()) {
      throw new IllegalArgumentException("at least one attribute is required");
    }
  }
}
