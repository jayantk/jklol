package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParameterSpec {

  SufficientStatistics getCurrentParameters();

  SufficientStatistics getNewParameters();

  int getId();

  ParameterSpec getParametersById(int id);
  
  ParameterSpec wrap(SufficientStatistics parameters);

  List<Object> toArgumentList();
}
