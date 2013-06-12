package com.jayantkrish.jklol.training;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Error thrown when LBFGS cannot find a suitable search direction.
 * This error is thrown when backtracking line search fails.
 *
 * @author jayantk
 */
public class LbfgsConvergenceError extends RuntimeException {
  private static final long serialVersionUID = 1L;
  
  private final SufficientStatistics finalParameters;
  private final SufficientStatistics searchDirection;
  private final double finalStepSize;
  private final int finalIteration;
  
  public LbfgsConvergenceError(String message, SufficientStatistics finalParameters,
      SufficientStatistics searchDirection, double finalStepSize, int finalIteration) {
    super(message);
    this.finalParameters = Preconditions.checkNotNull(finalParameters);
    this.searchDirection = Preconditions.checkNotNull(searchDirection);
    this.finalStepSize = finalStepSize;
    this.finalIteration = finalIteration;
  }

  public SufficientStatistics getFinalParameters() {
    return finalParameters;
  }

  public SufficientStatistics getSearchDirection() {
    return searchDirection;
  }

  public double getFinalStepSize() {
    return finalStepSize;
  }
  
  public int getFinalIteration() {
    return finalIteration;
  }
}
