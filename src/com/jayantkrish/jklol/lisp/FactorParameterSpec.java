package com.jayantkrish.jklol.lisp;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class FactorParameterSpec implements ParameterSpec {
  
  private final ParametricFactor factor;
  
  public FactorParameterSpec(ParametricFactor factor){
    this.factor = Preconditions.checkNotNull(factor);
  }

  @Override
  public int getParameterId() {
    return 0;
  }

  @Override
  public SufficientStatistics getParameters() {
    return factor.getNewSufficientStatistics();
  }

  @Override
  public SufficientStatistics getParametersById(int id) {
    throw new UnsupportedOperationException("not implemented");
  }
}
