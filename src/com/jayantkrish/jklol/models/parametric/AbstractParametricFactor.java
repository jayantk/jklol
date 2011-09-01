package com.jayantkrish.jklol.models.parametric;

import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * Implementations of common {@link ParametricFactor} methods.
 * 
 * @param <T> type of parameters expected
 * @author jayantk
 */
public abstract class AbstractParametricFactor<T> implements ParametricFactor<T> {
  
  private final VariableNumMap variables;
  
  public AbstractParametricFactor(VariableNumMap variables) {
    this.variables = variables;
  }
  
  @Override
  public VariableNumMap getVars() {
    return variables;
  }
}
