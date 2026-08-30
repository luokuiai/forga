package com.luokuiai.forga.spring;

import com.luokuiai.forga.core.eval.CaveatEvaluator;
import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.CaveatRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.core.policy.PolicyRequirements;
import com.luokuiai.forga.resolver.ResolverRegistry;
import java.util.Set;

final class ForgaResolverValidator {

  private ForgaResolverValidator() {
  }

  static void validateCheckCapabilities(
      CompiledPolicy policy, ResolverRegistry resolvers, CaveatEvaluator caveats) {
    PolicyRequirements requirements = PolicyRequirements.from(policy);
    for (RelationRef relation : requirements.relations()) {
      if (resolvers.findForward(relation).isEmpty()) {
        throw new ForgaRuntimeException(
            "missing forward resolver for relation: " + relation.name());
      }
    }
    for (CaveatRef caveat : requirements.caveats()) {
      if (!supportedCaveats(caveats).contains(caveat)) {
        throw new ForgaRuntimeException("missing caveat evaluator for: " + caveat.name());
      }
      for (AttributeRef attribute : requiredAttributes(caveats, caveat)) {
        if (resolvers.findAttribute(attribute).isEmpty()) {
          throw new ForgaRuntimeException(
              "missing attribute resolver for caveat "
                  + caveat.name()
                  + ": "
                  + attribute.name());
        }
      }
    }
  }

  static boolean requiresGrantLookup(CompiledPolicy policy) {
    return PolicyRequirements.from(policy).grantLookupRequired();
  }

  private static Set<CaveatRef> supportedCaveats(CaveatEvaluator caveats) {
    try {
      return Set.copyOf(caveats.caveats());
    } catch (RuntimeException exception) {
      throw new ForgaRuntimeException("invalid caveat capability declaration");
    }
  }

  private static Set<AttributeRef> requiredAttributes(
      CaveatEvaluator caveats, CaveatRef caveat) {
    try {
      return Set.copyOf(caveats.requiredAttributes(caveat));
    } catch (RuntimeException exception) {
      throw new ForgaRuntimeException(
          "invalid attribute requirements for caveat: " + caveat.name());
    }
  }
}
