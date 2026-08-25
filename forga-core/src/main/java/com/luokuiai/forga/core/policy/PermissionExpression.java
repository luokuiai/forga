package com.luokuiai.forga.core.policy;

import com.luokuiai.forga.core.model.CaveatRef;
import com.luokuiai.forga.core.model.RelationRef;
import java.util.Collection;
import java.util.List;

/**
 * Immutable permission expression tree.
 */
public sealed interface PermissionExpression
    permits RelationExpression,
        UnionExpression,
        IntersectionExpression,
        ExclusionExpression,
        TraversalExpression,
        CaveatExpression,
        GrantExpression {

  /**
   * Creates a host-resolved effective permission grant expression.
   *
   * @return grant expression
   */
  static GrantExpression grant() {
    return new GrantExpression();
  }

  /**
   * Creates an expression that checks direct relation membership.
   *
   * @param relation relation to inspect
   * @return relation expression
   */
  static RelationExpression relation(RelationRef relation) {
    return new RelationExpression(relation);
  }

  /**
   * Creates an expression that allows any branch to match.
   *
   * @param expressions branches to evaluate
   * @return union expression
   */
  static UnionExpression union(Collection<? extends PermissionExpression> expressions) {
    return new UnionExpression(List.copyOf(expressions));
  }

  /**
   * Creates an expression that requires every branch to match.
   *
   * @param expressions branches to evaluate
   * @return intersection expression
   */
  static IntersectionExpression intersection(
      Collection<? extends PermissionExpression> expressions) {
    return new IntersectionExpression(List.copyOf(expressions));
  }

  /**
   * Creates an expression that subtracts one branch from another.
   *
   * @param base branch that grants access
   * @param excluded branch that denies access when matched
   * @return exclusion expression
   */
  static ExclusionExpression exclusion(PermissionExpression base, PermissionExpression excluded) {
    return new ExclusionExpression(base, excluded);
  }

  /**
   * Creates an expression that follows a relation before evaluating another expression.
   *
   * @param relation relation to traverse
   * @param expression expression evaluated after traversal
   * @return traversal expression
   */
  static TraversalExpression traversal(RelationRef relation, PermissionExpression expression) {
    return new TraversalExpression(relation, expression);
  }

  /**
   * Creates an expression that follows a relation to a declared object type before evaluating
   * another expression.
   *
   * @param relation relation to traverse
   * @param objectType caller-defined object type reached by the traversal
   * @param expression expression evaluated after traversal
   * @return traversal expression
   */
  static TraversalExpression traversal(
      RelationRef relation, String objectType, PermissionExpression expression) {
    return new TraversalExpression(relation, objectType, expression);
  }

  /**
   * Creates an expression guarded by a caveat.
   *
   * @param expression expression guarded by the caveat
   * @param caveat caveat to evaluate
   * @return caveat expression
   */
  static CaveatExpression caveat(PermissionExpression expression, CaveatRef caveat) {
    return new CaveatExpression(expression, caveat);
  }
}
