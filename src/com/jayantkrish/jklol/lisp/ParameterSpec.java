package com.jayantkrish.jklol.lisp;

import java.io.Serializable;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParameterSpec extends Serializable {

  SufficientStatistics getCurrentParameters();

  ParameterSpec wrap(SufficientStatistics parameters);

  SufficientStatistics getCurrentParametersByIds(int[] ids, SufficientStatistics parameters);

  SufficientStatistics getNewParameters();

  int getId();

  int[] getContainedIds();

  boolean containsId(int id);

  SufficientStatistics getParametersById(int id, SufficientStatistics parameters);
}
