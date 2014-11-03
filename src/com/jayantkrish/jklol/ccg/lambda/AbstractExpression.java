package com.jayantkrish.jklol.ccg.lambda;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
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
  public Set<ConstantExpression> getBoundVariables() {
    Set<ConstantExpression> variables = Sets.newHashSet();
    getBoundVariables(variables);
    return variables;
  }

  @Override
  public Expression renameVariables(List<ConstantExpression> variables,
      List<ConstantExpression> replacements) {
    Preconditions.checkArgument(variables.size() == replacements.size());
    Expression result = this;
    for (int i = 0; i < variables.size(); i++) {
      result = result.renameVariable(variables.get(i), replacements.get(i));
    }
    return result;
  }
  
  @Override
  public Expression freshenVariables(Collection<ConstantExpression> variables) {
    Set<ConstantExpression> boundVariables = getBoundVariables();
    Expression result = this;
    for (ConstantExpression var : variables) {
      if (boundVariables.contains(var)) {
        result = result.renameVariable(var, ConstantExpression.generateUniqueVariable());
      }
    }
    return result;
  }

  @Override
  public abstract int hashCode();
  
  @Override
  public abstract boolean equals(Object other);
}
