package com.jayantkrish.jklol.ccg.enumeratelf;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionExecutor;

public class DenotationRuleFilter implements EnumerationRuleFilter {
  
  private final ExpressionExecutor eval;
  
  public DenotationRuleFilter(ExpressionExecutor eval) {
    this.eval = Preconditions.checkNotNull(eval);
  }

  @Override
  public boolean apply(LfNode original, LfNode result) {
    if (!original.getType().equals(result.getType())) {
      return true;
    } 

    Optional<Object> originalDenotation = eval.evaluateSilent(original.getLf());
    Optional<Object> resultDenotation = eval.evaluateSilent(result.getLf());

    // System.out.println(original.getLf() + " " + originalDenotation + " " + result.getLf() + " " + resultDenotation);
    
    return originalDenotation.isPresent() && resultDenotation.isPresent() &&
        !originalDenotation.get().equals(resultDenotation.get());
  }
}
