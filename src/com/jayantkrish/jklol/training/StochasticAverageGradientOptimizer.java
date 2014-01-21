package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Stochastic average gradient is a variant of stochastic
 * gradient descent that has faster convergence properties
 * than stochastic gradient on strongly-convex objectives
 * with Lipschitz-continuous gradient. The method uses
 * old gradient information from every data point to produce
 * a better gradient approximation than using only a single
 * data point. See:
 * <p>
 * A Stochastic Gradient Method with an Exponential
 * Convergence Rate for Finite Training Sets. <br/>
 * Nicolas Le Roux, Mark Schmidt and Francis Bach.
 *
 * @author jayantk
 */
public class StochasticAverageGradientOptimizer implements GradientOptimizer {
  
  private final int numIterations;
  private final double l2Regularization;
  private final LogFunction log;

  private static final double MIN_GRADIENT_NORM_FOR_LIPSCHITZ = 1e-8;

  public StochasticAverageGradientOptimizer(int numIterations, double l2Regularization,
      LogFunction log) {
    Preconditions.checkArgument(numIterations >= 0);
    this.numIterations = numIterations;
    Preconditions.checkArgument(l2Regularization >= 0);
    this.l2Regularization = l2Regularization;

    this.log = (log != null) ? log : new NullLogFunction();
  }

  @Override
  public <M, E, T extends E> SufficientStatistics train(GradientOracle<M, E> oracle,
      SufficientStatistics initialParameters, Iterable<T> trainingData) {
    
    List<T> trainingDataList = Lists.newArrayList(trainingData);
    int trainingDataSize = trainingDataList.size();
    SufficientStatistics[] trainingDataGradients = new SufficientStatistics[trainingDataSize]; 
    SufficientStatistics stepDirection = oracle.initializeGradient();
    double lipschitzEstimate = 1.0;
    double lipschitzShrinkageFactor = Math.pow(2, -1.0 / trainingDataSize);
    for (int i = 0; i < numIterations; i++) {
      // Compute current gradient and update step direction.
      int exampleIndex = i % trainingDataSize;
      M currentModel = oracle.instantiateModel(initialParameters);
      SufficientStatistics currentGradient = oracle.initializeGradient();
      double objectiveValue = oracle.accumulateGradient(currentGradient, currentModel,
          trainingDataList.get(exampleIndex), log);

      // Apply l2 regularization if necessary.
      if (l2Regularization > 0.0) {
        currentGradient.increment(-1 * l2Regularization);
      }

      stepDirection.increment(currentGradient, 1.0);
      if (trainingDataGradients[exampleIndex] != null) {
        stepDirection.increment(trainingDataGradients[exampleIndex], -1.0);
      }
      trainingDataGradients[exampleIndex] = currentGradient;

      // Check Lipschitz condition and do line search update on 
      // Lipschitz constant if necessary.
      SufficientStatistics lipschitzPoint = initialParameters.duplicate();
      lipschitzPoint.increment(currentGradient, -1.0 / lipschitzEstimate);
      double lipschitzPointObjectiveValue = oracle.accumulateGradient(oracle.initializeGradient(),
          oracle.instantiateModel(lipschitzPoint), trainingDataList.get(exampleIndex), log);
      double gradientSquaredNorm = currentGradient.getL2Norm();
      gradientSquaredNorm = gradientSquaredNorm * gradientSquaredNorm;
      if (gradientSquaredNorm > MIN_GRADIENT_NORM_FOR_LIPSCHITZ && 
          lipschitzPointObjectiveValue <= objectiveValue - (0.5 * lipschitzEstimate * gradientSquaredNorm)) {
        lipschitzEstimate *= 2;
      } else {
        lipschitzEstimate *= lipschitzShrinkageFactor; 
      }

      // Take the step.
      double stepSize = 1.0;
      int numPointsWithGradients = Math.min(i + 1, trainingDataSize);
      double currentStepSize = stepSize / numPointsWithGradients;
      initialParameters.increment(stepDirection, currentStepSize);
    }
    return initialParameters;
  }
}
