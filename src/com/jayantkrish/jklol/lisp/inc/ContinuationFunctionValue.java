package com.jayantkrish.jklol.lisp.inc;

import java.util.List;

import com.jayantkrish.jklol.lisp.AmbEval.WrappedBuiltinFunction;
import com.jayantkrish.jklol.lisp.EvalContext;
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
  
  public Object apply(List<Object> args, EvalContext context) {
    return new WrappedBuiltinFunction(new FunctionValue() {
        public Object apply(List<Object> args2, EvalContext context2) {
          return continuationApply(args, args2, context, context2);
        }
    });
  }

  public abstract Object continuationApply(List<Object> args, List<Object> args2,
      EvalContext context, EvalContext context2);

}
