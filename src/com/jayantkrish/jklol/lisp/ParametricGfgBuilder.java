package com.jayantkrish.jklol.lisp;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;

public class ParametricGfgBuilder {

  private final ParametricFactorGraphBuilder fgBuilder;
  
  public ParametricGfgBuilder(ParametricFactorGraphBuilder fgBuilder) {
    this.fgBuilder = Preconditions.checkNotNull(fgBuilder);
  }

  public void addVariables(VariableNumMap newVariables) {
    fgBuilder.addVariables(newVariables);
  }

  /**
   * Adds an unparameterized factor to the model under construction.
   * 
   * @param factor
   */
  public void addConstantFactor(String factorName, Factor factor) {
    fgBuilder.addConstantFactor(factorName, factor);
  }

  public ParametricFactorGraph build() {
    return fgBuilder.build();
  }
}
