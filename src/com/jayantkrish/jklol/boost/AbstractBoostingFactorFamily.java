package com.jayantkrish.jklol.boost;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Common implementations of basic {@code BoostingFactorFamily} methods.
 * 
 * @author jayantk
 */
public abstract class AbstractBoostingFactorFamily implements BoostingFactorFamily {
  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap conditionalVars;
  private final VariableNumMap unconditionalVars;
  
  public AbstractBoostingFactorFamily(VariableNumMap conditionalVars, VariableNumMap unconditionalVars) {
    this.conditionalVars = Preconditions.checkNotNull(conditionalVars);
    this.unconditionalVars = Preconditions.checkNotNull(unconditionalVars);
    
    Preconditions.checkArgument(!conditionalVars.containsAny(unconditionalVars));
  }

  @Override
  public VariableNumMap getVariables() {
    return conditionalVars.union(unconditionalVars);
  }

  @Override
  public VariableNumMap getConditionalVariables() {
    return conditionalVars;
  }

  @Override
  public VariableNumMap getUnconditionalVariables() {
    return unconditionalVars;
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }
}
