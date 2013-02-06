package com.jayantkrish.jklol.ccg.lambda;

import java.io.Serializable;
import java.util.List;

public interface Expression extends Serializable {

  public List<Expression> getSubexpressions();
  
  public Expression substitute(ConstantExpression constant, Expression replacement);

  @Override
  public int hashCode();
  
  @Override
  public boolean equals(Object o);
  
  public Expression simplify();
}
