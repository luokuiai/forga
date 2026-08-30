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
   * Compiles an immutable policy definition.
   *
   * <p>Runtime resolver and caveat requirements are validated from the components registered with
   * the evaluator integration rather than from a caller-maintained duplicate capability list.
   *
   * @param definition policy definition
   * @return compiled policy
   */
  public static CompiledPolicy compile(PolicyDefinition definition) {
    Objects.requireNonNull(definition, "definition is required");
    definition.permissions().values().forEach(PolicyCompiler::validate);
    return new CompiledPolicy(definition, fingerprint(definition));
  }

  private static void validate(PermissionExpression expression) {
    if (expression instanceof RelationExpression relationExpression) {
      Objects.requireNonNull(relationExpression.relation(), "relation is required");
      return;
    }
    if (expression instanceof UnionExpression unionExpression) {
      unionExpression.expressions().forEach(PolicyCompiler::validate);
      return;
    }
    if (expression instanceof IntersectionExpression intersectionExpression) {
      intersectionExpression.expressions().forEach(PolicyCompiler::validate);
      return;
    }
    if (expression instanceof ExclusionExpression exclusionExpression) {
      validate(exclusionExpression.base());
      validate(exclusionExpression.excluded());
      return;
    }
    if (expression instanceof TraversalExpression traversalExpression) {
      Objects.requireNonNull(traversalExpression.relation(), "relation is required");
      validate(traversalExpression.expression());
      return;
    }
    if (expression instanceof CaveatExpression caveatExpression) {
      Objects.requireNonNull(caveatExpression.caveat(), "caveat is required");
      validate(caveatExpression.expression());
      return;
    }
    if (expression instanceof GrantExpression) {
      return;
    }
    throw new PolicyValidationException("unknown expression type");
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
