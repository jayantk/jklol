package com.jayantkrish.jklol.lisp.inc;

import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;

public interface IncEvalExample {
  
  public Expression2 getLogicalForm();
  
  public Object getDiagram();
  
  /**
   * Get a procedure for filtering evaluation states that are
   * consistent with the label of this example. The returned
   * predicate returns {@code true} if its argument is consistent
   * with the label, and {@code false} otherwise.
   * 
   * @return
   */
  public Predicate<IncEvalState> getLabelFilter();
}
