package com.jayantkrish.jklol.lisp;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParameterSpec {

  SufficientStatistics getCurrentParameters();
  
  SufficientStatistics getCurrentParametersByIds(int[] ids);

  SufficientStatistics getNewParameters();

  int getId();

  ParameterSpec getParametersById(int id);

  ParameterSpec wrap(SufficientStatistics parameters);

  /**
   * Gets the value of these parameters that should be passed
   * as an argument to the Lisp function family.
   * 
   * @return
   */
  Object toArgument();
}
