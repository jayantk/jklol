package com.jayantkrish.jklol.lisp;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;

public class TensorParameterSpec extends AbstractParameterSpec {

  private final SufficientStatistics currentParameters;
  private final VariableNumMap parameterVars;

  public TensorParameterSpec(int id, VariableNumMap parameterVars, 
      SufficientStatistics currentParameters) {
    super(id);
    this.parameterVars = Preconditions.checkNotNull(parameterVars);
    this.currentParameters = Preconditions.checkNotNull(currentParameters);
  }
  
  public static TensorParameterSpec zero(int id, VariableNumMap parameterVars) {
    SufficientStatistics parameters = new TensorSufficientStatistics(parameterVars,
        new DenseTensorBuilder(parameterVars.getVariableNumsArray(), parameterVars.getVariableSizes()));
    // TODO: remove this!
    parameters.perturb(1);
    
    return new TensorParameterSpec(id, parameterVars, parameters);
  }

  @Override
  public SufficientStatistics getCurrentParameters() {
    return currentParameters;
  }

  @Override
  public SufficientStatistics getNewParameters() {
    return new TensorSufficientStatistics(parameterVars,
        new DenseTensorBuilder(parameterVars.getVariableNumsArray(), parameterVars.getVariableSizes()));
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
    return new TensorParameterSpec(getId(), parameterVars, parameters);
  }

  public String toString() {
    return "parameters:" + currentParameters.getDescription();
  }
}
