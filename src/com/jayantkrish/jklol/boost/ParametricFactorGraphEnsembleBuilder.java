package com.jayantkrish.jklol.boost;

import com.jayantkrish.jklol.models.AbstractFactorGraphBuilder;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.util.IndexedList;

public class ParametricFactorGraphEnsembleBuilder extends AbstractFactorGraphBuilder<BoostingFactorFamily> {

  /**
   * Create an empty log-linear model builder
   */
  public ParametricFactorGraphEnsembleBuilder() {
    super();
  }

  /**
   * Get the factor graph being constructed with this builder.
   * 
   * @return
   */
  public ParametricFactorGraphEnsemble build() {
    return new ParametricFactorGraphEnsemble(new DynamicFactorGraph(variables, constantFactors, constantFactorNames),
        parametricFactors, factorPatterns, IndexedList.create(parametricFactorNames)); 
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
}
