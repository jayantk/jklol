package com.jayantkrish.jklol.lisp;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class FactorParameterSpec extends AbstractParameterSpec {

  private final ParametricFactor factor;
  private final SufficientStatistics currentParameters;

  public FactorParameterSpec(int id, ParametricFactor factor, SufficientStatistics currentParameters) {
    super(id);
    this.factor = Preconditions.checkNotNull(factor);
    this.currentParameters = Preconditions.checkNotNull(currentParameters);
  }

  public ParametricFactor getFactor() {
    return factor;
  }

  @Override
  public SufficientStatistics getCurrentParameters() {
    return currentParameters;
  }

  @Override
  public SufficientStatistics getNewParameters() {
    return factor.getNewSufficientStatistics();
  }

  @Override
  public SufficientStatistics getParametersById(int id, SufficientStatistics parameters) {
    if (id == this.getId()) {
      return parameters;
    } else {
      return null;
    }
  }

  @Override
  public ParameterSpec wrap(SufficientStatistics parameters) {
    return new FactorParameterSpec(getId(), factor, parameters);
  }

  public String toString() {
    return "parameters:" + currentParameters.getDescription();
  }
}
