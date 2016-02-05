package com.jayantkrish.jklol.ccg.gi;

import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.gi.IncrementalEval.IncrementalEvalState;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

public interface IncrementalEvalExample {
  
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
  public Predicate<IncrementalEvalState> getLabelFilter();
}
