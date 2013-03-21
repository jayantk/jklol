package com.jayantkrish.jklol.models.parametric;

import com.jayantkrish.jklol.models.AbstractFactorGraphBuilder;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;

/**
 * Builder for incrementally constructing {@link ParametricFactorGraph}s.
 * Depending on the types of factors added to the builder, this class can build
 * either a Bayesian network or a log-linear model.
 */
public class ParametricFactorGraphBuilder extends AbstractFactorGraphBuilder<ParametricFactor> {

  /**
   * Create an empty log-linear model builder
   */
  public ParametricFactorGraphBuilder() {
    super();
  }

  /**
   * Get the factor graph being constructed with this builder.
   * 
   * @return
   */
  public ParametricFactorGraph build() {
    return new ParametricFactorGraph(new DynamicFactorGraph(variables, constantFactors, constantFactorNames),
        parametricFactors, factorPatterns, parametricFactorNames); 
  }

  /**
   * Adds a parameterized, unreplicated factor to the model being constructed.
   * The factor will match only the variables which it is defined over.
   * 
   * @param factor
   */
  public void addUnreplicatedFactor(String factorName, ParametricFactor factor) {
    super.addUnreplicatedFactor(factorName, factor, factor.getVars());
  }
}