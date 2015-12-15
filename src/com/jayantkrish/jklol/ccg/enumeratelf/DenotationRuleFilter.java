package com.jayantkrish.jklol.ccg.enumeratelf;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionEvaluator;

public class DenotationRuleFilter implements EnumerationRuleFilter {
  
  private final ExpressionEvaluator eval;
  
  private static final String ERROR = "ERROR";
  
  public DenotationRuleFilter(ExpressionEvaluator eval) {
    this.eval = Preconditions.checkNotNull(eval);
  }

  @Override
  public boolean apply(LfNode original, LfNode result) {
    if (!original.getType().equals(result.getType())) {
      return true;
    } 

    Object originalDenotation = eval.evaluateSilentErrors(original.getLf(), ERROR);
    Object resultDenotation = eval.evaluateSilentErrors(result.getLf(), ERROR);

    return originalDenotation.equals(resultDenotation);
  }
}
