package com.luokuiai.forga.scope;

import com.luokuiai.forga.core.model.SubjectRef;
import java.util.Map;

/**
 * Request to check whether a subject can enter a target scope.
 *
 * @param subject subject requesting the switch
 * @param targetScope target scope
 * @param attributes request-scoped attributes
 */
public record ScopeSwitchRequest(
    SubjectRef subject,
    ScopeRef targetScope,
    Map<com.luokuiai.forga.core.model.AttributeRef, String> attributes) {

  /**
   * Creates a switch request without attributes.
   *
   * @param subject subject requesting the switch
   * @param targetScope target scope
   */
  public ScopeSwitchRequest(SubjectRef subject, ScopeRef targetScope) {
    this(subject, targetScope, Map.of());
  }

  /**
   * Creates a switch request.
   *
   * @param subject subject requesting the switch
   * @param targetScope target scope
   * @param attributes request-scoped attributes
   */
  public ScopeSwitchRequest {
    if (subject == null) {
      throw new IllegalArgumentException("subject is required");
    }
    if (targetScope == null) {
      throw new IllegalArgumentException("target scope is required");
    }
    attributes = Map.copyOf(attributes);
  }
}
