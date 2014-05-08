package com.jayantkrish.jklol.lisp;

import java.io.Serializable;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class SpecAndParameters implements Serializable {
  private static final long serialVersionUID = 1L;

  private final ParameterSpec spec;
  private final SufficientStatistics parameters;

  public SpecAndParameters(ParameterSpec spec, SufficientStatistics parameters) {
    this.spec = Preconditions.checkNotNull(spec);
    this.parameters = Preconditions.checkNotNull(parameters);
  }

  public ParameterSpec getParameterSpec() {
    return spec;
  }

  public SufficientStatistics getParameters() {
    return parameters;
  }
}