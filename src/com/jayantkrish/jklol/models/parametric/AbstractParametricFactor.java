package com.jayantkrish.jklol.models.parametric;

import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * Implementations of common {@link ParametricFactor} methods.
 * 
 * @author jayantk
 */
public abstract class AbstractParametricFactor implements ParametricFactor {
  
  private final VariableNumMap variables;
  
  public AbstractParametricFactor(VariableNumMap variables) {
    this.variables = variables;
  }
  
  @Override
  public VariableNumMap getVars() {
    return variables;
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics statistics) {
    return getParameterDescription(statistics, -1);
  }
}
