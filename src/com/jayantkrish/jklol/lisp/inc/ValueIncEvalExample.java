package com.jayantkrish.jklol.lisp.inc;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;

public class ValueIncEvalExample extends AbstractIncEvalExample {
  
  private final Object labelValue;
  
  public ValueIncEvalExample(Expression2 logicalForm, Object diagram,
      Object labelValue) {
    super(logicalForm, diagram);
    this.labelValue = Preconditions.checkNotNull(labelValue);
  }

  @Override
  public IncEvalCost getMarginCost() {
    return null;
  }

  @Override
  public IncEvalCost getLabelCost() {
    return new ValueIncEvalLoss(labelValue);
  }


  private static class ValueIncEvalLoss implements IncEvalCost {
    private final Object labelValue;
    
    public ValueIncEvalLoss(Object labelValue) {
      this.labelValue = labelValue;
    }

    @Override
    public double apply(IncEvalState state) {
      if (state.getContinuation() != null || labelValue.equals(state.getDenotation())) {
        return 0.0;
      } else {
        return Double.NEGATIVE_INFINITY;
      }
    }
  }
}
