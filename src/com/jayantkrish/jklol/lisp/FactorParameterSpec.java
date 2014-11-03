package com.jayantkrish.jklol.lisp;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class FactorParameterSpec extends AbstractParameterSpec {
  private static final long serialVersionUID = 2L;

  private final ParametricFactor factor;

  public FactorParameterSpec(int id, ParametricFactor factor) {
    super(id);
    this.factor = Preconditions.checkNotNull(factor);
  }

  public ParametricFactor getFactor() {
    return factor;
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
}
