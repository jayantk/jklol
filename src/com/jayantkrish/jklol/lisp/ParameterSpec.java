package com.jayantkrish.jklol.lisp;

import java.io.Serializable;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParameterSpec extends Serializable {

  SufficientStatistics getNewParameters();

  int getId();

  int[] getContainedIds();

  boolean containsId(int id);

  SufficientStatistics getParametersById(int id, SufficientStatistics parameters);

  SufficientStatistics getParametersByIds(int[] ids, SufficientStatistics parameters);
}
