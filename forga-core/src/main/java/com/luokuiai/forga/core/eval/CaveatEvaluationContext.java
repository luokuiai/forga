package com.luokuiai.forga.core.eval;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.SubjectRef;
import java.util.Map;
import java.util.Objects;

/**
 * Current caveat evaluation state.
 *
 * @param request original protected check including request-scoped attributes
 * @param object current object, which may differ after traversal
 * @param subject current subject
 * @param objectAttributes resolver-owned attributes for the current object
 */
public record CaveatEvaluationContext(
    CheckRequest request,
    ObjectRef object,
    SubjectRef subject,
    Map<AttributeRef, String> objectAttributes) {

  /** Creates a caveat evaluation context. */
  public CaveatEvaluationContext {
    request = Objects.requireNonNull(request, "request is required");
    object = Objects.requireNonNull(object, "object is required");
    subject = Objects.requireNonNull(subject, "subject is required");
    objectAttributes = Map.copyOf(objectAttributes);
  }
}
