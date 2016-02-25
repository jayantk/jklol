package com.jayantkrish.jklol.lisp.inc;

import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;

public interface IncEvalExample {
  
  public Expression2 getLogicalForm();
  
  public Object getDiagram();
  
  /**
   * Get a procedure for scoring evaluation states against
   * the label of this example. The returned
   * predicate returns a log probability indicating how
   * compatible the argument is with the label.
   */
  public IncEvalCost getIncEvalCost();

  /**
   * Predicate version of the cost function, returns {@code 0.0}
   * if argument is consistent with label, otherwise it
   * return {@code Double.NEGATIVE_INFINITY}
   */
  public IncEvalCost getIncEvalCostPredicate();
}
