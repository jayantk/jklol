package com.jayantkrish.jklol.lisp.inc;

import com.jayantkrish.jklol.ccg.lambda2.Expression2;

public interface IncEvalExample {
  
  public Expression2 getLogicalForm();
  
  public Object getDiagram();
  
  /**
   * Get a procedure for assigning cost for states
   * compared to the label of this example. The probability
   * of a state gets multiplied with exp({@code cost}).
   */
  public IncEvalCost getMarginCost();

  /**
   * This will typically return 0.0 for states matching the
   * label and {@code Double.NEGATIVE_INFINITY} otherwise.
   */
  public IncEvalCost getLabelCost();
}
