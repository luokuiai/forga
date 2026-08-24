package com.luokuiai.forga.spring;

import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.policy.CaveatExpression;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.core.policy.ExclusionExpression;
import com.luokuiai.forga.core.policy.IntersectionExpression;
import com.luokuiai.forga.core.policy.PermissionExpression;
import com.luokuiai.forga.core.policy.RelationExpression;
import com.luokuiai.forga.core.policy.TraversalExpression;
import com.luokuiai.forga.core.policy.UnionExpression;
import com.luokuiai.forga.resolver.ResolverRegistry;
import java.util.LinkedHashSet;
import java.util.Set;

final class ForgaResolverValidator {

  private ForgaResolverValidator() {
  }

  static void validateForwardCapabilities(CompiledPolicy policy, ResolverRegistry resolvers) {
    for (RelationRef relation : relations(policy)) {
      if (resolvers.findForward(relation).isEmpty()) {
        throw new ForgaRuntimeException(
            "missing forward resolver for relation: " + relation.name());
      }
    }
  }

  private static Set<RelationRef> relations(CompiledPolicy policy) {
    Set<RelationRef> relations = new LinkedHashSet<>();
    policy.definition().permissions().values()
        .forEach(expression -> collect(expression, relations));
    return relations;
  }

  private static void collect(PermissionExpression expression, Set<RelationRef> relations) {
    if (expression instanceof RelationExpression relationExpression) {
      relations.add(relationExpression.relation());
    } else if (expression instanceof UnionExpression unionExpression) {
      unionExpression.expressions().forEach(branch -> collect(branch, relations));
    } else if (expression instanceof IntersectionExpression intersectionExpression) {
      intersectionExpression.expressions().forEach(branch -> collect(branch, relations));
    } else if (expression instanceof ExclusionExpression exclusionExpression) {
      collect(exclusionExpression.base(), relations);
      collect(exclusionExpression.excluded(), relations);
    } else if (expression instanceof TraversalExpression traversalExpression) {
      relations.add(traversalExpression.relation());
      collect(traversalExpression.expression(), relations);
    } else if (expression instanceof CaveatExpression caveatExpression) {
      collect(caveatExpression.expression(), relations);
    }
  }
}
