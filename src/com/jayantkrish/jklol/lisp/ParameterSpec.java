package com.jayantkrish.jklol.lisp;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParameterSpec {

  int getParameterId();

  SufficientStatistics getParameters();

  SufficientStatistics getParametersById(int id);
}
