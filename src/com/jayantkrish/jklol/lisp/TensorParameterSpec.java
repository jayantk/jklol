package com.jayantkrish.jklol.lisp;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;

public class TensorParameterSpec extends AbstractParameterSpec {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap parameterVars;

  public TensorParameterSpec(int id, VariableNumMap parameterVars) {
    super(id);
    this.parameterVars = Preconditions.checkNotNull(parameterVars);
  }

  @Override
  public SufficientStatistics getNewParameters() {
    return new TensorSufficientStatistics(parameterVars,
        new DenseTensorBuilder(parameterVars.getVariableNumsArray(), parameterVars.getVariableSizes()));
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
