package com.jayantkrish.jklol.training;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Mappers;
import com.jayantkrish.jklol.util.Pseudorandom;

/**
 * An implementation of stochastic (sub)gradient ascent that can optimize any
 * function given by a {@link GradientOracle}.
 * 
 * @author jayantk
 */
public class StochasticGradientTrainer implements GradientOptimizer {

  private final long numIterations;
  private final int batchSize;
  private final LogFunction log;

  private final double stepSize;
  private final boolean decayStepSize;
  private final Regularizer regularizer;

  private final boolean returnAveragedParameters;
  private final boolean adaGrad;

  // Factor used to discount earlier observations in the moving average
  // estimates of the gradient norm and objective value. Smaller values
  // forget history faster.
  private static final double MOVING_AVG_DISCOUNT = 0.9;

  /**
   * Unregularized stochastic gradient descent.
   * 
   * @param numIterations
   * @param batchSize
   * @param stepSize
   * @param decayStepSize
   * @param log
   */
  public StochasticGradientTrainer(long numIterations, int batchSize,
      double stepSize, boolean decayStepSize, boolean returnAveragedParameters, LogFunction log) {
    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.log = (log != null) ? log : new NullLogFunction();

    this.stepSize = stepSize;
    this.decayStepSize = decayStepSize;
    this.returnAveragedParameters = returnAveragedParameters;
    this.adaGrad = false;
    this.regularizer = new StochasticL2Regularizer(0.0, 0.0);
  }

  /**
   * Regularized stochastic gradient descent, using {@code regularizer}.
   * 
   * @param numIterations
   * @param batchSize
   * @param stepSize
   * @param decayStepSize
   * @param regularizer
   * @param log
   */
  public StochasticGradientTrainer(long numIterations, int batchSize,
      double stepSize, boolean decayStepSize, boolean returnAveragedParameters, boolean adaGrad,
      Regularizer regularizer, LogFunction log) {
    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.log = (log != null) ? log : new NullLogFunction();

    this.stepSize = stepSize;
    this.decayStepSize = decayStepSize;
    this.returnAveragedParameters = returnAveragedParameters;
    this.adaGrad = adaGrad;
    this.regularizer = regularizer;
  }

  public static StochasticGradientTrainer createWithL2Regularization(long numIterations, int batchSize,
      double stepSize, boolean decayStepSize, boolean returnAveragedParameters, double l2Penalty, LogFunction log) {
    return new StochasticGradientTrainer(numIterations, batchSize, stepSize, decayStepSize,
        returnAveragedParameters, false, new StochasticL2Regularizer(l2Penalty, 1.0), log);
  }

  public static StochasticGradientTrainer createWithStochasticL2Regularization(long numIterations,
      int batchSize, double stepSize, boolean decayStepSize, boolean returnAveragedParameters, double l2Penalty,
      double regularizationFrequency, LogFunction log) {
    return new StochasticGradientTrainer(numIterations, batchSize, stepSize, decayStepSize, 
        returnAveragedParameters, false, new StochasticL2Regularizer(l2Penalty, regularizationFrequency), log);
  }

  public static StochasticGradientTrainer createAdagrad(long numIterations,
      int batchSize, double stepSize, boolean decayStepSize, boolean returnAveragedParameters,
      double l2Penalty, double regularizationFrequency, LogFunction log) {
    return new StochasticGradientTrainer(numIterations, batchSize, stepSize, decayStepSize, 
        returnAveragedParameters, true, new AdagradL2Regularizer(l2Penalty, regularizationFrequency), log);
  }

  public static StochasticGradientTrainer createWithL1Regularization(long numIterations, int batchSize,
      double stepSize, boolean decayStepSize, boolean returnAveragedParameters, double l1Penalty, LogFunction log) {
    return new StochasticGradientTrainer(numIterations, batchSize, stepSize, decayStepSize,
        returnAveragedParameters, false, new L1Regularizer(l1Penalty), log);
  }

  @Override
  public <M, E, T extends E> SufficientStatistics train(GradientOracle<M, E> oracle,
      SufficientStatistics initialParameters, Iterable<T> trainingData) {

    // cycledTrainingData loops indefinitely over the elements of trainingData.
    // This is desirable because we want batchSize examples but don't
    // particularly care where in trainingData they come from.
    Iterator<T> cycledTrainingData = Iterators.cycle(trainingData);

    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();

    SufficientStatistics averagedParameters = null;
    if (returnAveragedParameters) {
      // Compute the average of the parameter values from each iteration  
      // by tracking the sum of the parameters, then dividing.
      averagedParameters = oracle.initializeGradient();
      averagedParameters.increment(initialParameters, 1.0);
    }
    
    SufficientStatistics gradientSumSquares = null;
    if (adaGrad) {
      gradientSumSquares = oracle.initializeGradient();
    }

    double gradientL2 = 0.0;
    GradientEvaluation gradientAccumulator = null;
    // This is an attempt at estimating how much the parameters are still
    // changing.
    double exponentiallyWeightedUpdateNorm = stepSize;
    double exponentiallyWeightedObjectiveValue = 0.0;
    double exponentiallyWeightedDenom = 0.0;
    int totalSearchErrors = 0;
    for (long i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      log.startTimer("serialize_parameters");
      log.logParameters(i, initialParameters);
      log.stopTimer("serialize_parameters");

      // Get the examples for this batch. Ideally, this would be a random
      // sample; however, deterministically iterating over the examples is
      // more efficient and is fairly close if the examples are provided in
      // random order.
      log.startTimer("instantiate_model");
      List<T> batchData = getBatch(cycledTrainingData, batchSize);
      M currentModel = oracle.instantiateModel(initialParameters);
      log.stopTimer("instantiate_model");

      log.startTimer("compute_gradient_(serial)");
      Mapper<T, T> mapper = Mappers.<T>identity();
      GradientReducer<M, T> reducer = new GradientReducer<M, T>(currentModel, initialParameters,
          oracle, log);
      gradientAccumulator = executor.mapReduce(batchData, mapper, reducer, gradientAccumulator);

      totalSearchErrors += gradientAccumulator.getSearchErrors();
      SufficientStatistics gradient = gradientAccumulator.getGradient();
      if (batchSize > 1) {
        gradient.multiply(1.0 / batchSize);
      }

      log.stopTimer("compute_gradient_(serial)");

      log.startTimer("parameter_update");
      // Apply regularization and take a gradient step.
      double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(i + 2)) : stepSize;
      regularizer.apply(gradient, initialParameters, gradientSumSquares, currentStepSize);

      // System.out.println(initialParameters);
      log.stopTimer("parameter_update");

      log.startTimer("compute_statistics");
      gradientL2 = gradient.getL2Norm();
      double objectiveValue = gradientAccumulator.getObjectiveValue() / batchSize;
      exponentiallyWeightedUpdateNorm = gradientL2 
          + (MOVING_AVG_DISCOUNT * exponentiallyWeightedUpdateNorm);
      exponentiallyWeightedObjectiveValue = objectiveValue
          + (MOVING_AVG_DISCOUNT * exponentiallyWeightedObjectiveValue);
      exponentiallyWeightedDenom = 1 + (MOVING_AVG_DISCOUNT * exponentiallyWeightedDenom);
      log.stopTimer("compute_statistics");

      if (returnAveragedParameters) {
        log.startTimer("average_parameters");
        averagedParameters.increment(initialParameters, 1.0 / numIterations);
        log.stopTimer("average_parameters");
      }

      log.logStatistic(i, "search errors", totalSearchErrors);
      log.logStatistic(i, "gradient l2 norm", gradientL2);
      log.logStatistic(i, "step size", currentStepSize);
      log.logStatistic(i, "objective value", objectiveValue);
      log.logStatistic(i, "objective value (moving avg.)", exponentiallyWeightedObjectiveValue
          / exponentiallyWeightedDenom);
      log.logStatistic(i, "gradient l2 norm (moving avg.)", exponentiallyWeightedUpdateNorm
          / exponentiallyWeightedDenom);

      gradientAccumulator.zeroOut();
      log.notifyIterationEnd(i);
    }

    if (returnAveragedParameters) {
      return averagedParameters;
    } else {
      return initialParameters;
    }
  }

  private <S> List<S> getBatch(Iterator<S> trainingData, int batchSize) {
    List<S> batchData = Lists.newArrayListWithCapacity(batchSize);
    for (int i = 0; i < batchSize && trainingData.hasNext(); i++) {
      batchData.add(trainingData.next());
    }
    return batchData;
  }

  /**
   * A regularization penalty applicable to gradients during gradient descent.
   * 
   * @author jayantk
   */
  public static interface Regularizer {
    /**
     * Updates {@code currentParameters} with the result of taking a gradient
     * step in the direction of {@code gradient} and applying a regularization
     * penalty. May mutate {@code gradient}, and will mutate
     * {@code currentParameters}.
     * 
     * @param gradient
     * @param currentParameters
     * @param gradientSumSquares
     * @param currentStepSize
     * @return
     */
    public void apply(SufficientStatistics gradient, SufficientStatistics currentParameters,
        SufficientStatistics gradientSumSquares, double currentStepSize);
  }

  /**
   * An L2 regularization penalty that is applied on random iterations. 
   * Regularization can be the most expensive part of training, since it
   * touches every parameter. Using a randomized regularizer reduces the
   * frequency of regularization, thereby improving speed.
   *    
   * @author jayantk
   */
  public static class StochasticL2Regularizer implements Regularizer {
    private final double l2Penalty;
    private final double frequency;

    public StochasticL2Regularizer(double l2Penalty, double frequency) {
      Preconditions.checkArgument(l2Penalty >= 0.0);
      Preconditions.checkArgument(frequency >= 0.0 && frequency <= 1.0);
      this.l2Penalty = l2Penalty;
      this.frequency = frequency;
    }

    @Override
    public void apply(SufficientStatistics gradient, SufficientStatistics currentParameters,
        SufficientStatistics gradientSumSquares, double currentStepSize) {
      Preconditions.checkArgument(gradientSumSquares == null);
      double rand = Pseudorandom.get().nextDouble();
      if (rand < frequency && l2Penalty != 0.0) {
        // Objective value calculation:
        // objectiveValue -= l2Penalty * currentParameters.getL2Norm() / (2.0 * frequency);
        currentParameters.multiply(1.0 - (currentStepSize * l2Penalty) / frequency);
      } 
      currentParameters.increment(gradient, currentStepSize);
    }
  }

  /**
   * The Adagrad stochastic gradient update with an L2 regularization
   * penalty that is applied on random iterations.
   *    
   * @author jayantk
   */
  public static class AdagradL2Regularizer implements Regularizer {
    private final double l2Penalty;
    private final double frequency;

    public AdagradL2Regularizer(double l2Penalty, double frequency) {
      Preconditions.checkArgument(l2Penalty >= 0.0);
      Preconditions.checkArgument(frequency >= 0.0 && frequency <= 1.0);
      this.l2Penalty = l2Penalty;
      this.frequency = frequency;
    }

    @Override
    public void apply(SufficientStatistics gradient, SufficientStatistics currentParameters,
        SufficientStatistics gradientSumSquares, double currentStepSize) {
      Preconditions.checkNotNull(gradientSumSquares);
      double rand = Pseudorandom.get().nextDouble();
      if (rand < frequency && l2Penalty != 0.0) {
        double curPenalty = l2Penalty / frequency;
        gradientSumSquares.incrementSquareAdagrad(gradient, currentParameters, -1.0 * curPenalty);
        currentParameters.multiplyInverseAdagrad(gradientSumSquares, 1.0, -1.0 * currentStepSize * curPenalty);

      } else {
        gradientSumSquares.incrementSquare(gradient, 1.0);
      }

      currentParameters.incrementAdagrad(gradient, gradientSumSquares, currentStepSize);
    }
  }

  /**
   * An L1 regularization penalty, i.e., a penalty on the sum of absolute values
   * of the weights. This regularizer implements the truncated gradient method
   * described in:
   * <p> 
   * Sparse online learning via truncated gradient. John Langford, Lihong Li,
   * and Tong Zhang. Journal of Machine Learning Research 10 (2009).
   * 
   * @author jayantk
   */
  public static class L1Regularizer implements Regularizer {
    private final double l1Penalty;

    public L1Regularizer(double l1Penalty) {
      Preconditions.checkArgument(l1Penalty >= 0.0);
      this.l1Penalty = l1Penalty;
    }

    @Override
    public void apply(SufficientStatistics gradient, SufficientStatistics currentParameters,
        SufficientStatistics gradientSumSquares, double currentStepSize) {
      Preconditions.checkArgument(gradientSumSquares == null);
      currentParameters.increment(gradient, currentStepSize);
      currentParameters.softThreshold(currentStepSize * l1Penalty);
    }
  }
}
