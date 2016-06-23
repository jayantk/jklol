package com.jayantkrish.jklol.ccg.lambda2;

import com.google.common.base.Optional;

public interface ExpressionExecutor {

  public Object evaluate(Expression2 lf);
  
  public Object evaluate(Expression2 lf, Object context);
  
  public Optional<Object> evaluateSilent(Expression2 lf);
  
  public Optional<Object> evaluateSilent(Expression2 lf, Object context);
}
