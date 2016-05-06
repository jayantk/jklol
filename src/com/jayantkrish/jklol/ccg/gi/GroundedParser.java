package com.jayantkrish.jklol.ccg.gi;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ShiftReduceStack;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEvalState;

public class GroundedParser {
  private final CcgParser parser;
  private final IncEval incrEval;
  
  public GroundedParser(CcgParser parser, IncEval incrEval) {
    this.parser = Preconditions.checkNotNull(parser);
    this.incrEval = Preconditions.checkNotNull(incrEval);
  }
  
  public CcgParser getCcgParser() {
    return parser;
  }
  
  public IncEval getEval() {
    return incrEval;
  }

  /**
   * A search state for joint CCG parsing and incremental evaluation.
   * 
   * @author jayantk
   */
  public static class State {
    /**
     * Current state of the shift reduce CCG parser.
     */
    public final ShiftReduceStack stack;
    public final Object diagram;
    public final Environment env;
    
    /**
     * Current state of incremental evaluation. If {@code null},
     * the next search actions are parser actions; otherwise,
     * the next actions are evaluation actions. 
     */
    public final IncEvalState evalResult;
    
    public final double totalProb;
    
    public State(ShiftReduceStack stack, Object diagram, Environment env, IncEvalState evalResult) {
      this.stack = Preconditions.checkNotNull(stack);
      this.diagram = diagram;
      this.env = env;
      this.evalResult = evalResult;
      
      if (evalResult != null) {
        this.totalProb = stack.totalProb * evalResult.getProb();
      } else {
        this.totalProb = stack.totalProb;
      }
    }
  }  
}
