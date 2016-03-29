package com.jayantkrish.jklol.lisp;

import com.jayantkrish.jklol.training.LogFunction;

public class EvalContext {
  private final LogFunction log;
  
  public EvalContext(LogFunction log) {
    this.log = log;
  }
  
  public LogFunction getLog() {
    return log;
  }
}
