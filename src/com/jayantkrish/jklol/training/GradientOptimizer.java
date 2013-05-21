package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Optimization algorithm that only uses gradient (first-order)
 * information.
 * 
 * @author jayantk
 */
public interface GradientOptimizer {

  /**
   * Optimizes the function given by {@code oracle} and
   * {@code trainingData} using {@code initialParameters} as the
   * starting point for optimization. This method may mutate
   * {@code initialParameters}.
   * 
   * @param oracle
   * @param initialParameters
   * @param trainingData
   * @return the optimized parameters
   */
  public <M, E, T extends E> SufficientStatistics train(GradientOracle<M, E> oracle,
      SufficientStatistics initialParameters, Iterable<T> trainingData);
}
