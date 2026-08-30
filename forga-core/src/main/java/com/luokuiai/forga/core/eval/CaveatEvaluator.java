package com.luokuiai.forga.core.eval;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.CaveatRef;
import java.util.Set;

/** Evaluates declared caveats against request and resolver-owned object attributes. */
public interface CaveatEvaluator {

  /**
   * Returns caveats supported by this evaluator.
   *
   * @return immutable caveat capabilities
   */
  Set<CaveatRef> caveats();

  /**
   * Returns object attributes required to evaluate a caveat.
   *
   * @param caveat caveat to inspect
   * @return immutable required attributes
   */
  Set<AttributeRef> requiredAttributes(CaveatRef caveat);

  /**
   * Evaluates a caveat.
   *
   * @param caveat caveat to evaluate
   * @param context current evaluation context
   * @return true when the caveat passes
   */
  boolean evaluate(CaveatRef caveat, CaveatEvaluationContext context);

  /**
   * Returns an evaluator that declares and allows no caveats.
   *
   * @return deny-all caveat evaluator
   */
  static CaveatEvaluator denyAll() {
    return new CaveatEvaluator() {
      @Override
      public Set<CaveatRef> caveats() {
        return Set.of();
      }

      @Override
      public Set<AttributeRef> requiredAttributes(CaveatRef caveat) {
        return Set.of();
      }

      @Override
      public boolean evaluate(CaveatRef caveat, CaveatEvaluationContext context) {
        return false;
      }
    };
  }
}
