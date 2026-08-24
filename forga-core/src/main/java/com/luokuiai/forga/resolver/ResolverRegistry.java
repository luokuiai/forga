package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.RelationRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable registry of resolver declarations.
 */
public final class ResolverRegistry {

  private final Map<String, RelationshipResolver> byName;

  private final Map<RelationRef, RelationshipResolver> forward;

  private final Map<RelationRef, RelationshipResolver> reverse;

  private final Map<AttributeRef, RelationshipResolver> attributes;

  /**
   * Creates a resolver registry.
   *
   * @param resolvers resolvers to register
   */
  public ResolverRegistry(List<? extends RelationshipResolver> resolvers) {
    List<RelationshipResolver> copy = List.copyOf(resolvers);
    ensureUniqueNames(copy);
    Map<String, RelationshipResolver> names = new LinkedHashMap<>();
    Map<RelationRef, RelationshipResolver> forwardCapabilities = new LinkedHashMap<>();
    Map<RelationRef, RelationshipResolver> reverseCapabilities = new LinkedHashMap<>();
    Map<AttributeRef, RelationshipResolver> attributeCapabilities = new LinkedHashMap<>();
    for (RelationshipResolver resolver : copy) {
      names.put(resolver.descriptor().name(), resolver);
      resolver
          .descriptor()
          .forwardRelations()
          .forEach(
              relation ->
                  register("forward relation", relation, resolver, forwardCapabilities));
      resolver
          .descriptor()
          .reverseRelations()
          .forEach(
              relation ->
                  register("reverse relation", relation, resolver, reverseCapabilities));
      resolver
          .descriptor()
          .attributes()
          .forEach(
              attribute ->
                  register("attribute", attribute, resolver, attributeCapabilities));
    }
    byName = Map.copyOf(names);
    forward = Map.copyOf(forwardCapabilities);
    reverse = Map.copyOf(reverseCapabilities);
    attributes = Map.copyOf(attributeCapabilities);
  }

  /**
   * Returns all resolvers.
   *
   * @return immutable resolvers
   */
  public List<RelationshipResolver> resolvers() {
    return List.copyOf(byName.values());
  }

  /**
   * Finds a resolver that supports forward resolution for a relation.
   *
   * @param relation relation to inspect
   * @return matching resolver
   */
  public Optional<RelationshipResolver> findForward(RelationRef relation) {
    return Optional.ofNullable(forward.get(relation));
  }

  /**
   * Finds a resolver that supports reverse resolution for a relation.
   *
   * @param relation relation to inspect
   * @return matching resolver
   */
  public Optional<RelationshipResolver> findReverse(RelationRef relation) {
    return Optional.ofNullable(reverse.get(relation));
  }

  /**
   * Finds a resolver that supports an attribute.
   *
   * @param attribute attribute to inspect
   * @return matching resolver
   */
  public Optional<RelationshipResolver> findAttribute(AttributeRef attribute) {
    return Optional.ofNullable(attributes.get(attribute));
  }

  private static void ensureUniqueNames(List<RelationshipResolver> resolvers) {
    List<String> names = new ArrayList<>();
    for (RelationshipResolver resolver : resolvers) {
      String name = resolver.descriptor().name();
      if (names.contains(name)) {
        throw new IllegalArgumentException("duplicate resolver name: " + name);
      }
      names.add(name);
    }
  }

  private static <T> void register(
      String capability,
      T reference,
      RelationshipResolver resolver,
      Map<T, RelationshipResolver> registrations) {
    RelationshipResolver existing = registrations.putIfAbsent(reference, resolver);
    if (existing != null) {
      throw new IllegalArgumentException(
          "duplicate "
              + capability
              + " capability: "
              + reference
              + " declared by "
              + existing.descriptor().name()
              + " and "
              + resolver.descriptor().name());
    }
  }
}
