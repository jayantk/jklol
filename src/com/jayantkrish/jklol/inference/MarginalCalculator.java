package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.models.FactorGraph;

/**
 * A {@code MarginalCalculator} is an algorithm for computing (possibly
 * approximate) marginal distributions of a factor graph.
 */
public interface MarginalCalculator {

  /**
   * Compute (unconditional) marginal distributions over the factors in the
   * factor graph.
   */
  public MarginalSet computeMarginals(FactorGraph factorGraph);

  /**
   * Compute unconditional max marginals.
   */
  public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph);
}
