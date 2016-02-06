package com.jayantkrish.jklol.lisp.inc;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

public abstract class AbstractIncEvalExample implements IncEvalExample {
  
  private final Expression2 logicalForm;
  private final Object diagram;

  public AbstractIncEvalExample(Expression2 logicalForm, Object diagram) {
    this.logicalForm = Preconditions.checkNotNull(logicalForm);
    // Even if you do not reference the diagram in your evaluation code,
    // it should be non-null to ensure that evaluation succeeds.
    this.diagram = Preconditions.checkNotNull(diagram);
  }

  @Override
  public Expression2 getLogicalForm() {
      return logicalForm;
  }

  @Override
  public Object getDiagram() {
    return diagram;
  }
}
