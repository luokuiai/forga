package com.luokuiai.forga.resolver;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.RelationRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable registry of host resolver capabilities. */
public final class ResolverRegistry {

  private final Map<String, Resolver> byName;

  private final Map<RelationRef, ForwardRelationshipResolver> forward;

  private final Map<RelationRef, ReverseRelationshipResolver> reverse;

  private final Map<AttributeRef, AttributeResolver> attributes;

  /**
   * Creates a resolver registry from actual host resolver implementations.
   *
   * @param resolvers resolvers to register
   */
  public ResolverRegistry(List<? extends Resolver> resolvers) {
    List<Resolver> copy = List.copyOf(resolvers);
    Map<String, Resolver> names = new LinkedHashMap<>();
    Map<RelationRef, ForwardRelationshipResolver> forwardCapabilities = new LinkedHashMap<>();
    Map<RelationRef, ReverseRelationshipResolver> reverseCapabilities = new LinkedHashMap<>();
    Map<AttributeRef, AttributeResolver> attributeCapabilities = new LinkedHashMap<>();
    for (Resolver resolver : copy) {
      String name = requiredName(resolver.name());
      Resolver existingName = names.putIfAbsent(name, resolver);
      if (existingName != null) {
        throw new IllegalArgumentException("duplicate resolver name: " + name);
      }
      if (resolver instanceof ForwardRelationshipResolver forwardResolver) {
        copySet(forwardResolver.forwardRelations(), "forward relations")
            .forEach(
                relation ->
                    register(
                        "forward relation",
                        relation,
                        forwardResolver,
                        forwardCapabilities));
      }
      if (resolver instanceof ReverseRelationshipResolver reverseResolver) {
        copySet(reverseResolver.reverseRelations(), "reverse relations")
            .forEach(
                relation ->
                    register(
                        "reverse relation", relation, reverseResolver, reverseCapabilities));
      }
      if (resolver instanceof AttributeResolver attributeResolver) {
        copySet(attributeResolver.attributes(), "attributes")
            .forEach(
                attribute ->
                    register("attribute", attribute, attributeResolver, attributeCapabilities));
      }
    }
    byName = Map.copyOf(names);
    forward = Map.copyOf(forwardCapabilities);
    reverse = Map.copyOf(reverseCapabilities);
    attributes = Map.copyOf(attributeCapabilities);
  }

  /**
   * Returns all named resolver implementations.
   *
   * @return immutable resolvers
   */
  public List<Resolver> resolvers() {
    return List.copyOf(byName.values());
  }

  /**
   * Finds the owner of a forward relation.
   *
   * @param relation relation to inspect
   * @return matching resolver
   */
  public Optional<ForwardRelationshipResolver> findForward(RelationRef relation) {
    return Optional.ofNullable(forward.get(relation));
  }

  /**
   * Finds the owner of a reverse relation.
   *
   * @param relation relation to inspect
   * @return matching resolver
   */
  public Optional<ReverseRelationshipResolver> findReverse(RelationRef relation) {
    return Optional.ofNullable(reverse.get(relation));
  }

  /**
   * Finds the owner of an object attribute.
   *
   * @param attribute attribute to inspect
   * @return matching resolver
   */
  public Optional<AttributeResolver> findAttribute(AttributeRef attribute) {
    return Optional.ofNullable(attributes.get(attribute));
  }

  private static String requiredName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("resolver name is required");
    }
    return name.trim();
  }

  private static <T, R extends Resolver> void register(
      String capability, T reference, R resolver, Map<T, R> registrations) {
    if (reference == null) {
      throw new IllegalArgumentException(capability + " is required");
    }
    R existing = registrations.putIfAbsent(reference, resolver);
    if (existing != null) {
      throw new IllegalArgumentException(
          "duplicate "
              + capability
              + " capability: "
              + reference
              + " declared by "
              + existing.name()
              + " and "
              + resolver.name());
    }
  }

  private static <T> Set<T> copySet(Set<T> values, String name) {
    if (values == null) {
      throw new IllegalArgumentException(name + " are required");
    }
    return Set.copyOf(values);
  }
}
