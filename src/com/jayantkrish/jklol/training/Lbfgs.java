package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mappers;

/**
 * Implementation of the L-BFGS algorithm for optimizing smooth,
 * convex objectives. L-BFGS is a quasi-Newton method that relies on a
 * {@link GradientOracle} to provide function information.
 * <p>
 * Note that, for L-BFGS to be applicable, the objective being
 * optimized must be smooth and convex. Specifically, the convexity
 * requirement means that this algorithm should not be applied to
 * problems with hidden variables.
 * 
 * @author jayantk
 */
public class Lbfgs implements GradientOptimizer {
  
  // Negative maxIterations means run to convergence.
  private final int maxIterations;
  private final int numVectorsInApproximation;
  private final double l2Regularization;

  private final LogFunction log;
  
  private final double minStepSize;
  private final double gradientConvergenceThreshold;

  private static final double LINE_SEARCH_CONSTANT = 0.5;

  private static final double WOLFE_CONDITION_C1 = 1e-4;
  private static final double WOLFE_CONDITION_C2 = 0.9;

  public Lbfgs(int maxIterations, int numVectorsInApproximation,
      double l2Regularization, LogFunction log) {
    this.maxIterations = maxIterations;
    this.numVectorsInApproximation = numVectorsInApproximation;
    this.l2Regularization = l2Regularization;
    
    this.minStepSize = 1e-20;
    this.gradientConvergenceThreshold = 1e-6;

    this.log = Preconditions.checkNotNull(log);
  }
  
  public Lbfgs(int maxIterations, int numVectorsInApproximation,
      double l2Regularization, double minStepSize, double gradientConvergenceThreshold,
      LogFunction log) {
    this.maxIterations = maxIterations;
    this.numVectorsInApproximation = numVectorsInApproximation;
    this.l2Regularization = l2Regularization;
    
    this.minStepSize = minStepSize;
    this.gradientConvergenceThreshold = gradientConvergenceThreshold;

    this.log = Preconditions.checkNotNull(log);
  }

  public int getMaxIterations() {
    return maxIterations;
  }

  public int getNumVectorsInApproximation() {
    return numVectorsInApproximation;
  }

  public double getL2Regularization() {
    return l2Regularization;
  }

  public LogFunction getLog() {
    return log;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Throws {@link LbfgsConvergenceError} if a suitable step size 
   * cannot be found by backtracking line search.
   * 
   * @param oracle
   * @param initialParameters
   * @param trainingData
   * @return
   */
  @Override
  public <M, E, T extends E> SufficientStatistics train(GradientOracle<M, E> oracle,
      SufficientStatistics initialParameters, Iterable<T> trainingData) {
    SufficientStatistics currentParameters = initialParameters;
    SufficientStatistics previousParameters = null;
    SufficientStatistics previousGradient = null;
    List<SufficientStatistics> pointDeltas = Lists.newArrayList();
    List<SufficientStatistics> gradientDeltas = Lists.newArrayList();
    List<Double> scalings = Lists.newArrayList();

    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    List<T> dataList = Lists.newArrayList(trainingData);
    GradientEvaluation gradientEvaluation = null;
    for (int i = 0; i < maxIterations || maxIterations < 0; i++) {
      log.notifyIterationStart(i);
      log.logParameters(i, initialParameters);

      if (gradientEvaluation == null) {
        gradientEvaluation = evaluateGradient(currentParameters, dataList,
            oracle, executor, log);
      }
      SufficientStatistics gradient = gradientEvaluation.getGradient();
      
      double gradientL2Norm = gradient.getL2Norm();
      if (gradientL2Norm < gradientConvergenceThreshold) {
        return currentParameters;
      }

      // We haven't converged yet. Figure out which direction to move in.
      log.startTimer("compute_search_direction");
      // Store the requisite data for approximating the inverse
      // Hessian.
      if (previousParameters != null) {
        SufficientStatistics pointDelta = currentParameters.duplicate();
        pointDelta.increment(previousParameters, -1.0);
        pointDeltas.add(pointDelta);

        // Note that gradient and previousGradient are actually the
        // *negative* gradient (i.e., a descent direction).
        SufficientStatistics gradientDelta = previousGradient.duplicate();
        gradientDelta.increment(gradient, -1.0);
        gradientDeltas.add(gradientDelta);

        scalings.add(1.0 / (pointDelta.innerProduct(gradientDelta)));
        
        int firstUnusedIndex = i - (numVectorsInApproximation + 1);
        if (firstUnusedIndex >= 0) {
          // Free up memory used by portions of the inverse Hessian
          // approximation which are no longer used.
          pointDeltas.set(firstUnusedIndex, null);
          gradientDeltas.set(firstUnusedIndex, null);
        }
      }

      previousParameters = currentParameters.duplicate();
      previousGradient = gradient.duplicate();

      // Compute this iteration's search direction.
      int hessianVectorCount = (int) Math.min(numVectorsInApproximation, i);
      SufficientStatistics direction = gradient.duplicate();
      direction.multiply(-1.0);
      double[] weights = new double[hessianVectorCount];
      for (int j = 0; j < hessianVectorCount; j++) {
        int index = i - (j + 1);
        double weight = scalings.get(index) * (pointDeltas.get(index).innerProduct(direction));
        direction.increment(gradientDeltas.get(index), -1.0 * weight);
        weights[hessianVectorCount - (j + 1)] = weight;
      }

      // The assumption here is that the initial Hessian estimate is
      // the identity. Multiply direction by the Hessian estimate here
      // to pick another value.
      for (int j = 0; j < hessianVectorCount; j++) {
        int index = i + j - hessianVectorCount;
        double weight = scalings.get(index) * (gradientDeltas.get(index).innerProduct(direction));
        direction.increment(pointDeltas.get(index), weights[j] - weight);
      }
      log.stopTimer("compute_search_direction");

      log.logStatistic(i, "parameter l2 norm", previousParameters.getL2Norm());
      log.logStatistic(i, "gradient l2 norm", gradientL2Norm);
      log.logStatistic(i, "direction l2 norm", direction.getL2Norm());
      log.logStatistic(i, "search errors", gradientEvaluation.getSearchErrors());
      log.logStatistic(i, "objective value", gradientEvaluation.getObjectiveValue());

      log.startTimer("compute_step_size");
      // Perform a backtracking line search to find a step size.
      double stepSize = 1.0 / LINE_SEARCH_CONSTANT;
      double currentObjectiveValue = gradientEvaluation.getObjectiveValue();
      double nextObjectiveValue, curInnerProd, cond1Rhs, nextInnerProd, cond2Rhs;
      SufficientStatistics nextParameters;

      do {
        stepSize = stepSize * LINE_SEARCH_CONSTANT;
        nextParameters = currentParameters.duplicate();
        nextParameters.increment(direction, -1.0 * stepSize);
        gradientEvaluation = evaluateGradient(nextParameters, dataList, oracle, executor, log);

        // Check the Wolfe conditions to ensure sufficient descent.
        nextObjectiveValue = gradientEvaluation.getObjectiveValue();
        curInnerProd = gradient.innerProduct(direction);
        SufficientStatistics nextGradient = gradientEvaluation.getGradient();
        nextInnerProd = nextGradient.innerProduct(direction);

        cond1Rhs = currentObjectiveValue - (WOLFE_CONDITION_C1 * stepSize * curInnerProd);
        cond2Rhs = -1.0 * WOLFE_CONDITION_C2 * curInnerProd;
      } while ((nextObjectiveValue <= cond1Rhs || Double.isNaN(cond1Rhs) || Double.isNaN(nextObjectiveValue))
          && stepSize > minStepSize);

      log.logStatistic(i, "step size", stepSize);
      log.stopTimer("compute_step_size");

      if (stepSize <= minStepSize) {
        throw new LbfgsConvergenceError("L-BFGS could not find a suitable step size.",
            currentParameters, direction, stepSize, i);
      }
      currentParameters = nextParameters;

      log.notifyIterationEnd(i);
    }
    return currentParameters;
  }

  private <M, E, T extends E> GradientEvaluation evaluateGradient(SufficientStatistics parameters,
      List<T> dataList, GradientOracle<M, E> oracle, MapReduceExecutor executor,
      LogFunction log) {
    // Create the factor graph (or whatever else) from the parameter
    // vector.
    log.startTimer("factor_graph_from_parameters");
    M nextModel = oracle.instantiateModel(parameters);
    log.stopTimer("factor_graph_from_parameters");

    // In parallel, compute the gradient from the entire training
    // set. Note that this computation does not include the added
    // regularization term.
    log.startTimer("compute_gradient_(serial)");
    GradientEvaluation evaluation = executor.mapReduce(dataList,
        Mappers.<T>identity(), new GradientReducer<M, T>(nextModel, parameters, oracle, log));
    log.stopTimer("compute_gradient_(serial)");

    // Normalize the objective term, then apply regularization
    evaluation.getGradient().multiply(1.0 / dataList.size());
    evaluation.setObjectiveValue(evaluation.getObjectiveValue() / dataList.size());

    double oldNorm = evaluation.getGradient().getL2Norm();
    if (Double.isNaN(oldNorm)) {
      System.out.println("Objective norm is NaN");
    }

    if (l2Regularization > 0.0) {
      evaluation.getGradient().increment(parameters, -1.0 * l2Regularization);
      double parameterSumSquares = parameters.getL2Norm();
      parameterSumSquares *= parameterSumSquares;
      evaluation.setObjectiveValue(evaluation.getObjectiveValue() - (l2Regularization * parameterSumSquares / 2.0));
    }

    return evaluation;
  }
}