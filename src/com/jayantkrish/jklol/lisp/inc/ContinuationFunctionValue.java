package com.jayantkrish.jklol.lisp.inc;

import com.jayantkrish.jklol.lisp.FunctionValue;
import com.jayantkrish.jklol.training.LogFunction;

public abstract class ContinuationFunctionValue implements FunctionValue {

  protected IncEvalChart chart;
  protected IncEvalState current;
  protected ContinuationIncEval eval;
  protected LogFunction log;

  public ContinuationFunctionValue() {}

  public void setChart(IncEvalChart chart, IncEvalState current, ContinuationIncEval eval,
      LogFunction log) {
    this.chart = chart;
    this.current = current;
    this.eval = eval;
    this.log = log;
  }

  public abstract ContinuationFunctionValue copy();
}
