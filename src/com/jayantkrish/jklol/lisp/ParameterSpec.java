package com.jayantkrish.jklol.lisp;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParameterSpec {

  SufficientStatistics getCurrentParameters();
  
  SufficientStatistics getCurrentParametersByIds(int[] ids);

  SufficientStatistics getNewParameters();

  int getId();

  ParameterSpec getParametersById(int id);

  ParameterSpec wrap(SufficientStatistics parameters);
}
