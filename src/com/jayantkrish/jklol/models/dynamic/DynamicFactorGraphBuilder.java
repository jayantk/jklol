package com.jayantkrish.jklol.models.dynamic;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;

public class DynamicFactorGraphBuilder {
  private DynamicVariableSet variables;
  private List<PlateFactor> plateFactors;
  private List<String> plateFactorNames;

  public DynamicFactorGraphBuilder(DynamicVariableSet variables,
      List<PlateFactor> plateFactors, List<String> plateFactorNames) {
    this.variables = variables;
    this.plateFactors = Lists.newArrayList(plateFactors);
    this.plateFactorNames = Lists.newArrayList(plateFactorNames);
  }

  public DynamicVariableSet getVariables() {
    return variables;
  }
  
  /**
   * Adds an unreplicated factor to the model being
   * constructed. The factor will match only the variables which it is
   * defined over.
   * 
   * @param factor
   */
  public void addUnreplicatedFactor(String factorName, Factor factor, VariableNumMap vars) {
    plateFactors.add(new ReplicatedFactor(factor, new WrapperVariablePattern(vars)));
    plateFactorNames.add(factorName);
  }

  public VariableNumMap addVariable(String name, Variable variable) {
    variables = variables.addFixedVariable(name, variable);
    return variables.getFixedVariables().getVariablesByName(name);
  }

  public DynamicFactorGraph build() {
    return new DynamicFactorGraph(variables, plateFactors, plateFactorNames);
  }
}
