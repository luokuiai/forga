package com.luokuiai.forga.core.policy;

import com.luokuiai.forga.core.model.PermissionRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compiles and validates immutable policy definitions.
 */
public final class PolicyCompiler {

  private PolicyCompiler() {
  }

  /**
   * Compiles a policy after checking required resolver capabilities.
   *
   * @param definition policy definition
   * @param capabilities supported resolver capabilities
   * @return compiled policy
   */
  public static CompiledPolicy compile(
      PolicyDefinition definition, ResolverCapabilities capabilities) {
    Objects.requireNonNull(definition, "definition is required");
    Objects.requireNonNull(capabilities, "capabilities are required");
    definition.permissions()
        .forEach((permission, expression) -> validate(expression, capabilities));
    return new CompiledPolicy(definition, fingerprint(definition));
  }

  private static void validate(
      PermissionExpression expression, ResolverCapabilities capabilities) {
    if (expression instanceof RelationExpression relationExpression) {
      requireRelation(relationExpression.relation(), capabilities);
      return;
    }
    if (expression instanceof UnionExpression unionExpression) {
      unionExpression.expressions().forEach(branch -> validate(branch, capabilities));
      return;
    }
    if (expression instanceof IntersectionExpression intersectionExpression) {
      intersectionExpression.expressions().forEach(branch -> validate(branch, capabilities));
      return;
    }
    if (expression instanceof ExclusionExpression exclusionExpression) {
      validate(exclusionExpression.base(), capabilities);
      validate(exclusionExpression.excluded(), capabilities);
      return;
    }
    if (expression instanceof TraversalExpression traversalExpression) {
      requireRelation(traversalExpression.relation(), capabilities);
      validate(traversalExpression.expression(), capabilities);
      return;
    }
    if (expression instanceof CaveatExpression caveatExpression) {
      requireCaveat(caveatExpression.caveat(), capabilities);
      validate(caveatExpression.expression(), capabilities);
      return;
    }
    if (expression instanceof GrantExpression) {
      return;
    }
    throw new PolicyValidationException("unknown expression type");
  }

  private static void requireRelation(
      com.luokuiai.forga.core.model.RelationRef relation, ResolverCapabilities capabilities) {
    if (!capabilities.supports(relation)) {
      throw new PolicyValidationException("unsupported relation: " + relation.name());
    }
  }

  private static void requireCaveat(
      com.luokuiai.forga.core.model.CaveatRef caveat, ResolverCapabilities capabilities) {
    if (!capabilities.supports(caveat)) {
      throw new PolicyValidationException("unsupported caveat: " + caveat.name());
    }
  }

  private static String fingerprint(PolicyDefinition definition) {
    String canonical =
        definition.permissions().entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().name()))
            .map(PolicyCompiler::canonicalPermission)
            .collect(Collectors.joining(";"));
    return "sha256:" + sha256(canonical);
  }

  private static String canonicalPermission(
      Map.Entry<PermissionRef, PermissionExpression> entry) {
    return "permission(" + entry.getKey().name() + ")=" + canonical(entry.getValue());
  }

  private static String canonical(PermissionExpression expression) {
    if (expression instanceof RelationExpression relationExpression) {
      return "relation(" + relationExpression.relation().name() + ")";
    }
    if (expression instanceof UnionExpression unionExpression) {
      return branches("union", unionExpression.expressions());
    }
    if (expression instanceof IntersectionExpression intersectionExpression) {
      return branches("intersection", intersectionExpression.expressions());
    }
    if (expression instanceof ExclusionExpression exclusionExpression) {
      return "exclusion("
          + canonical(exclusionExpression.base())
          + ","
          + canonical(exclusionExpression.excluded())
          + ")";
    }
    if (expression instanceof TraversalExpression traversalExpression) {
      return "traversal("
          + traversalExpression.relation().name()
          + ","
          + traversalExpression.objectType().orElse("")
          + ","
          + canonical(traversalExpression.expression())
          + ")";
    }
    if (expression instanceof CaveatExpression caveatExpression) {
      return "caveat("
          + canonical(caveatExpression.expression())
          + ","
          + caveatExpression.caveat().name()
          + ")";
    }
    if (expression instanceof GrantExpression) {
      return "grant()";
    }
    throw new PolicyValidationException("unknown expression type");
  }

  private static String branches(String name, Iterable<PermissionExpression> expressions) {
    String joined =
        java.util.stream.StreamSupport.stream(expressions.spliterator(), false)
            .map(PolicyCompiler::canonical)
            .collect(Collectors.joining(","));
    return name + "(" + joined + ")";
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
