package com.jayantkrish.jklol.ccg.lambda;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;

public class ConstantExpression implements Expression {
  private static final long serialVersionUID = 1L;
  
  private final String name;
  
  public ConstantExpression(String name) {
    this.name = Preconditions.checkNotNull(name);
  }
  
  public String getName() {
    return name;
  }
  
  @Override
  public List<Expression> getSubexpressions() {
    return Collections.emptyList();
  }
  
  @Override
  public Expression substitute(ConstantExpression constant, Expression replacement) {
    if (this.equals(constant)) {
      return replacement;
    } else {
      return this;
    }
  }

  @Override
  public Expression simplify() {
    return this;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ConstantExpression other = (ConstantExpression) obj;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    return true;
  }
}
