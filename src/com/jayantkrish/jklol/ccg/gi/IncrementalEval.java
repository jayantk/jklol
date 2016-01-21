package com.jayantkrish.jklol.ccg.gi;

import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.util.KbestHeap;

public interface IncrementalEval {

  public void evaluateContinuation(State state, KbestHeap<State> heap);
  
  public Object lfToContinuation(Expression2 lf);
  
  public Environment getEnvironment(State currentState);
  
  public boolean isEvaluatable(HeadedSyntacticCategory syntax);
}
