package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;

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

  @Override
  public SufficientStatistics getCurrentParameters() {
    return currentParameters;
  }

  @Override
  public SufficientStatistics getNewParameters() {
    return factor.getNewSufficientStatistics();
  }

  @Override
  public ParameterSpec getParametersById(int id) {
    if (id == this.getId()) {
      return this;
    } else {
      return null;
    }
  }
  
  @Override
  public ParameterSpec wrap(SufficientStatistics parameters) {
    return new FactorParameterSpec(getId(), factor, parameters);
  }
  
  @Override
  public List<Object> toArgumentList() {
    return Arrays.<Object>asList(this);
  }
}
