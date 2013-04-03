package com.jayantkrish.jklol.boost;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.AbstractFactorGraphBuilder;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.util.IndexedList;

public class ParametricFactorGraphEnsembleBuilder extends AbstractFactorGraphBuilder<BoostingFactorFamily> {
  
  private List<Factor> baseFactors;

  /**
   * Create an empty log-linear model builder
   */
  public ParametricFactorGraphEnsembleBuilder() {
    super();
    this.baseFactors = Lists.newArrayList();
  }

  /**
   * Get the factor graph being constructed with this builder.
   * 
   * @return
   */
  public ParametricFactorGraphEnsemble build() {
    return new ParametricFactorGraphEnsemble(
        new DynamicFactorGraph(variables, constantFactors, constantFactorNames),
        parametricFactors, factorPatterns, baseFactors, IndexedList.create(parametricFactorNames));
  }

  /**
   * Adds a parameterized, unreplicated factor to the model being constructed.
   * The factor will match only the variables which it is defined over.
   * 
   * @param factor
   */
  public void addUnreplicatedFactor(String factorName, BoostingFactorFamily factor) {
    super.addUnreplicatedFactor(factorName, factor, factor.getVariables());
  }

  @Override 
  public void addFactor(String factorName, BoostingFactorFamily factor, VariablePattern factorPattern) {
    addFactor(factorName, factor, null, factorPattern);
  }
  
  public void addFactor(String factorName, BoostingFactorFamily factor, Factor baseFactor,
      VariablePattern factorPattern) {
    Preconditions.checkArgument(baseFactor == null || factor.getVariables().equals(baseFactor.getVars()));
    super.addFactor(factorName, factor, factorPattern);
    baseFactors.add(baseFactor);
  }
  
  @Override
  public void addUnreplicatedFactor(String factorName, BoostingFactorFamily factor, VariableNumMap vars) {
    addUnreplicatedFactor(factorName, factor, null, vars);
  }
  
  public void addUnreplicatedFactor(String factorName, BoostingFactorFamily factor, Factor baseFactor,
      VariableNumMap vars) {
    Preconditions.checkArgument(baseFactor == null || factor.getVariables().equals(baseFactor.getVars()));
    super.addUnreplicatedFactor(factorName, factor, vars);
    baseFactors.add(baseFactor);
  }
}
