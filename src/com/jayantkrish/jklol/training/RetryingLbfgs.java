package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Implementation of LBFGS that handles convergence errors by 
 * restarting the optimization at the final location with a new
 * inverse Hessian approximation.
 * 
 * @author jayantk
 */
public class RetryingLbfgs {
  
  private final int maxIterations;
  private final int numVectorsInApproximation;
  private final double l2Regularization;

  private final LogFunction log;

  public RetryingLbfgs(int maxIterations, int numVectorsInApproximation,
      double l2Regularization, LogFunction log) {
    this.maxIterations = maxIterations;
    this.numVectorsInApproximation = numVectorsInApproximation;
    this.l2Regularization = l2Regularization;
    this.log = log;
  }

  public <M, E> SufficientStatistics train(GradientOracle<M, E> oracle,
      SufficientStatistics initialParameters, Iterable<E> trainingData) {
    int completedIterations = 0;
    SufficientStatistics parameters = initialParameters;
    while (completedIterations < maxIterations) {
      Lbfgs lbfgs = new Lbfgs(maxIterations - completedIterations, numVectorsInApproximation, 
          l2Regularization, log);
      try {
        parameters = lbfgs.train(oracle, parameters, trainingData);
        completedIterations = maxIterations;
      } catch (LbfgsConvergenceError error) {
        log.logMessage("L-BFGS Convergence Failed. Restarting L-BFGS.");
        parameters = error.getFinalParameters();
	if (error.getFinalIteration() == 0) {
	    // If the first iteration fails, retrying it isn't going to help.
	    completedIterations = maxIterations;
	} else {
	    completedIterations += error.getFinalIteration() + 1;
	}
      }
    }
    return parameters;
  }
}
