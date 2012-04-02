package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.models.FactorGraph;

/**
 * A {@code MarginalCalculator} is an algorithm for computing (possibly
 * approximate) marginal distributions of a factor graph.
 */
public interface MarginalCalculator {

  /**
   * Compute (unconditional) marginal distributions over the factors in the
   * factor graph. Throws {@code ZeroProbabilityError} if a search error occurs.
   * Note that such errors should only occur if this {@code MarginalCalculator}
   * is performing approximate inference.
   */
  public MarginalSet computeMarginals(FactorGraph factorGraph);

  /**
   * Compute unconditional max marginals. Throws {@code ZeroProbabilityError} if
   * a search error occurs. Note that such errors should only occur if this
   * {@code MarginalCalculator} is performing approximate inference.
   */
  public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph);

  public static class ZeroProbabilityError extends RuntimeException {
    /**
     * Auto-generated by Eclipse.
     */
    private static final long serialVersionUID = 4302665674086795397L;

  }
}
