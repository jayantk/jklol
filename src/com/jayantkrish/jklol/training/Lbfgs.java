package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.training.GradientMapper.GradientEvaluation;

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
public class Lbfgs {

  private final int numIterations;
  private final int numVectorsInApproximation;
  private final double l2Regularization;

  private final LogFunction log;

  private static final double WOLFE_CONDITION_C1 = 1e-4;
  private static final double WOLFE_CONDITION_C2 = 0.9;

  public Lbfgs(int numIterations, int numVectorsInApproximation,
      double l2Regularization, LogFunction log) {
    this.numIterations = numIterations;
    this.numVectorsInApproximation = numVectorsInApproximation;
    this.l2Regularization = l2Regularization;

    this.log = Preconditions.checkNotNull(log);
  }

  public <M, E> SufficientStatistics train(GradientOracle<M, E> oracle,
      SufficientStatistics initialParameters, Iterable<E> trainingData) {

    SufficientStatistics currentParameters = initialParameters;

    SufficientStatistics previousParameters = null;
    SufficientStatistics previousGradient = null;
    List<SufficientStatistics> pointDeltas = Lists.newArrayList();
    List<SufficientStatistics> gradientDeltas = Lists.newArrayList();
    List<Double> scalings = Lists.newArrayList();

    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    List<E> dataList = Lists.newArrayList(trainingData);
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);

      // Create the factor graph (or whatever else) from the parameter
      // vector.
      log.startTimer("factor_graph_from_parameters");
      M currentModel = oracle.instantiateModel(currentParameters);
      log.stopTimer("factor_graph_from_parameters");

      // In parallel, compute the gradient from the entire training
      // set. Note that this computation does not include the added
      // regularization term.
      log.startTimer("compute_gradient_(serial)");
      GradientEvaluation gradientEvaluation = executor.mapReduce(dataList,
          new GradientMapper<M, E>(currentModel, oracle, log), new GradientReducer<M, E>(oracle, log));
      SufficientStatistics gradient = gradientEvaluation.getGradient();
      log.stopTimer("compute_gradient_(serial)");

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
      }

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
      previousParameters = currentParameters.duplicate();
      previousGradient = gradient.duplicate();
      log.stopTimer("compute_search_direction");
      
      log.logStatistic(i, "gradient l2 norm", gradient.getL2Norm());

      log.startTimer("compute_step_size");
      // Perform a backtracking line search to find a step size.
      double stepSize = 2.0;
      double currentObjectiveValue = gradientEvaluation.getObjectiveValue();
      double nextObjectiveValue, curInnerProd, nextInnerProd;
      SufficientStatistics nextParameters;
      do {
        stepSize = stepSize / 2;
        nextParameters = currentParameters.duplicate();
        nextParameters.increment(direction, -1.0 * stepSize);
        M nextModel = oracle.instantiateModel(nextParameters);
        gradientEvaluation = executor.mapReduce(dataList,
            new GradientMapper<M, E>(nextModel, oracle, log), new GradientReducer<M, E>(oracle, log));

        // Check the Wolfe conditions to ensure sufficient descent.
        nextObjectiveValue = gradientEvaluation.getObjectiveValue();
        curInnerProd = gradient.innerProduct(direction);
        nextInnerProd = gradientEvaluation.getGradient().innerProduct(direction);

        System.out.println("current: " + currentObjectiveValue);
        System.out.println("next: " + nextObjectiveValue);
        System.out.println("curInnerProd: " + curInnerProd);
        System.out.println("nextInnerProd: " + nextInnerProd);
      } while (nextObjectiveValue < currentObjectiveValue - (WOLFE_CONDITION_C1 * stepSize * curInnerProd) ||
          nextInnerProd < WOLFE_CONDITION_C2 * curInnerProd);
      log.logStatistic(i, "step size", stepSize);
      log.stopTimer("compute_step_size");

      currentParameters = nextParameters;

      log.notifyIterationEnd(i);
    }
    return currentParameters;
  }
}