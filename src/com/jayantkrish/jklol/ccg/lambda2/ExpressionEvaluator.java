package com.jayantkrish.jklol.ccg.lambda2;

public interface ExpressionEvaluator {

  public Object evaluate(Expression2 lf);
  
  public Object evaluateSilentErrors(Expression2 lf, String errorValue);
}
