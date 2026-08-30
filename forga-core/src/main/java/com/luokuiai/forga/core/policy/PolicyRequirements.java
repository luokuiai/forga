package com.luokuiai.forga.core.policy;

import com.luokuiai.forga.core.model.CaveatRef;
import com.luokuiai.forga.core.model.RelationRef;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Runtime capabilities referenced by a compiled policy.
 *
 * @param relations forward relations required for checks
 * @param caveats caveats required for evaluation
 * @param grantLookupRequired whether the policy contains a dynamic grant leaf
 */
public record PolicyRequirements(
    Set<RelationRef> relations, Set<CaveatRef> caveats, boolean grantLookupRequired) {

  /** Creates immutable policy requirements. */
  public PolicyRequirements {
    relations = Set.copyOf(relations);
    caveats = Set.copyOf(caveats);
  }

  /**
   * Discovers runtime requirements from a compiled policy.
   *
   * @param policy compiled policy
   * @return immutable runtime requirements
   */
  public static PolicyRequirements from(CompiledPolicy policy) {
    Set<RelationRef> relations = new LinkedHashSet<>();
    Set<CaveatRef> caveats = new LinkedHashSet<>();
    boolean grantRequired = false;
    for (PermissionExpression expression : policy.definition().permissions().values()) {
      grantRequired |= collect(expression, relations, caveats);
    }
    return new PolicyRequirements(relations, caveats, grantRequired);
  }

  private static boolean collect(
      PermissionExpression expression,
      Set<RelationRef> relations,
      Set<CaveatRef> caveats) {
    if (expression instanceof RelationExpression relationExpression) {
      relations.add(relationExpression.relation());
      return false;
    } else if (expression instanceof UnionExpression unionExpression) {
      return unionExpression.expressions().stream()
          .map(branch -> collect(branch, relations, caveats))
          .reduce(false, Boolean::logicalOr);
    } else if (expression instanceof IntersectionExpression intersectionExpression) {
      return intersectionExpression.expressions().stream()
          .map(branch -> collect(branch, relations, caveats))
          .reduce(false, Boolean::logicalOr);
    } else if (expression instanceof ExclusionExpression exclusionExpression) {
      boolean base = collect(exclusionExpression.base(), relations, caveats);
      return collect(exclusionExpression.excluded(), relations, caveats) || base;
    } else if (expression instanceof TraversalExpression traversalExpression) {
      relations.add(traversalExpression.relation());
      return collect(traversalExpression.expression(), relations, caveats);
    } else if (expression instanceof CaveatExpression caveatExpression) {
      caveats.add(caveatExpression.caveat());
      return collect(caveatExpression.expression(), relations, caveats);
    } else if (expression instanceof GrantExpression) {
      return true;
    }
    return false;
  }
}
