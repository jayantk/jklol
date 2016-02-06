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
  public Predicate<IncEvalState> getLabelFilter() {
    return new ValuePredicate(labelValue);
  }
  
  private static class ValuePredicate implements Predicate<IncEvalState> {
    private final Object labelValue;
    
    public ValuePredicate(Object labelValue) {
      this.labelValue = labelValue;
    }

    @Override
    public boolean apply(IncEvalState state) {
      return state.getContinuation() != null || labelValue.equals(state.getDenotation());
    }
  }
}
