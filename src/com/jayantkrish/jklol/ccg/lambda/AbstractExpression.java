package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import com.google.common.base.Preconditions;

public abstract class AbstractExpression implements Expression {
  private static final long serialVersionUID = 1L;
  
  private final List<Expression> subexpressions;
  
  public AbstractExpression(List<Expression> subexpressions) {
    this.subexpressions = Preconditions.checkNotNull(subexpressions);
  }

  @Override
  public List<Expression> getSubexpressions() {
    return subexpressions;
  }
}
