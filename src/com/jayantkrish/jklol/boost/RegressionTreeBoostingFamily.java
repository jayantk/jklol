package com.jayantkrish.jklol.boost;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class RegressionTreeBoostingFamily extends AbstractBoostingFactorFamily {

  public RegressionTreeBoostingFamily(VariableNumMap conditionalVars,
      VariableNumMap unconditionalVars) {
    super(conditionalVars, unconditionalVars);
  }

  @Override
  public FunctionalGradient getNewFunctionalGradient() {
    return FactorFunctionalGradient.empty();
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics statistics) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void incrementGradient(FunctionalGradient gradient, Factor marginal,
      Assignment assignment) {
    // TODO Auto-generated method stub

  }

  @Override
  public SufficientStatistics projectGradient(FunctionalGradient gradient) {
    // TODO Auto-generated method stub
    return null;
  }
}
