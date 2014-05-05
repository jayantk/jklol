package com.jayantkrish.jklol.lisp;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParameterSpec {

  SufficientStatistics getCurrentParameters();

  ParameterSpec wrap(SufficientStatistics parameters);

  
  SufficientStatistics getCurrentParametersByIds(int[] ids, SufficientStatistics parameters);

  SufficientStatistics getNewParameters();

  int getId();

  int[] getContainedIds();

  boolean containsId(int id);

  SufficientStatistics getParametersById(int id, SufficientStatistics parameters);
}
