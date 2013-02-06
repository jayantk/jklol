package com.jayantkrish.jklol.ccg.lambda;

import java.util.Set;

import com.google.common.collect.Sets;

/**
 * Common implementations of {@code Expression} methods.
 * 
 * @author jayantk
 */
public abstract class AbstractExpression implements Expression {
  private static final long serialVersionUID = 1L;

  @Override
  public Set<ConstantExpression> getFreeVariables() {
    Set<ConstantExpression> variables = Sets.newHashSet();
    getFreeVariables(variables);
    return variables;
  }
  
  @Override
  public abstract int hashCode();
  
  @Override
  public abstract boolean equals(Object other);
}
