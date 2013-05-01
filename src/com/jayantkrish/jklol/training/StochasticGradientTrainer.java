package com.jayantkrish.jklol.training;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.training.GradientMapper.GradientEvaluation;
import com.jayantkrish.jklol.util.Pseudorandom;

/**
 * An implementation of stochastic (sub)gradient ascent that can optimize any
 * function given by a {@link GradientOracle}.
 * 
 * @author jayantk
 */
public class StochasticGradientTrainer {

  private final int numIterations;
  private final int batchSize;
  private final LogFunction log;

  private final double stepSize;
  private final boolean decayStepSize;
  private final Regularizer regularizer;

  /**
   * Unregularized stochastic gradient descent.
   * 
   * @param numIterations
   * @param batchSize
   * @param stepSize
   * @param decayStepSize
   * @param log
   */
  public StochasticGradientTrainer(int numIterations, int batchSize,
      double stepSize, boolean decayStepSize, LogFunction log) {
    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.log = (log != null) ? log : new NullLogFunction();

    this.stepSize = stepSize;
    this.decayStepSize = decayStepSize;
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
  public StochasticGradientTrainer(int numIterations, int batchSize,
      double stepSize, boolean decayStepSize, Regularizer regularizer, LogFunction log) {
    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.log = (log != null) ? log : new NullLogFunction();

    this.stepSize = stepSize;
    this.decayStepSize = decayStepSize;
    this.regularizer = regularizer;
  }

  public static StochasticGradientTrainer createWithL2Regularization(int numIterations, int batchSize,
      double stepSize, boolean decayStepSize, double l2Penalty, LogFunction log) {
    return new StochasticGradientTrainer(numIterations, batchSize, stepSize, decayStepSize,
        new StochasticL2Regularizer(l2Penalty, 1.0), log);
  }

  public static StochasticGradientTrainer createWithStochasticL2Regularization(int numIterations,
      int batchSize, double stepSize, boolean decayStepSize, double l2Penalty,
      double regularizationFrequency, LogFunction log) {
    return new StochasticGradientTrainer(numIterations, batchSize, stepSize, decayStepSize, 
        new StochasticL2Regularizer(l2Penalty, regularizationFrequency), log);
  }

  public static StochasticGradientTrainer createWithL1Regularization(int numIterations, int batchSize,
      double stepSize, boolean decayStepSize, double l1Penalty, LogFunction log) {
    return new StochasticGradientTrainer(numIterations, batchSize, stepSize, decayStepSize, new L1Regularizer(l1Penalty), log);
  }

  public <M, E, T extends E> SufficientStatistics train(GradientOracle<M, E> oracle,
      SufficientStatistics initialParameters, Iterable<T> trainingData) {

    // cycledTrainingData loops indefinitely over the elements of trainingData.
    // This is desirable because we want batchSize examples but don't
    // particularly care where in trainingData they come from.
    Iterator<T> cycledTrainingData = Iterators.cycle(trainingData);

    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();

    double gradientL2 = 0.0;
    // This is an attempt at estimating how much the parameters are still
    // changing.
    // When it drops below some threshold, we'll say the algorithm has
    // converged.
    double exponentiallyWeightedUpdateNorm = stepSize;
    double exponentiallyWeightedObjectiveValue = 0.0;
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);

      // Get the examples for this batch. Ideally, this would be a random
      // sample; however, deterministically iterating over the examples is
      // more efficient and is fairly close if the examples are provided in
      // random order.
      log.startTimer("factor_graph_from_parameters");
      List<T> batchData = getBatch(cycledTrainingData, batchSize);
      M currentModel = oracle.instantiateModel(initialParameters);
      log.stopTimer("factor_graph_from_parameters");

      log.startTimer("compute_gradient_(serial)");
      int iterSearchErrors = 0;
      GradientMapper<M, T> mapper = new GradientMapper<M, T>(currentModel, oracle, log);
      GradientReducer<M, T> reducer = new GradientReducer<M, T>(oracle, log);
      GradientEvaluation oracleResult = null;
      if (batchSize == 1) {
        log.startTimer("initialize_reducer");
        GradientEvaluation result = reducer.getInitialValue();
        log.stopTimer("initialize_reducer");

        oracleResult = mapper.map(batchData.get(0));

        log.startTimer("reduce");
        oracleResult = reducer.reduce(oracleResult, result);
        log.stopTimer("reduce");
      } else {
        oracleResult = executor.mapReduce(batchData, mapper, reducer);
      }

      iterSearchErrors = oracleResult.getSearchErrors();
      SufficientStatistics gradient = oracleResult.getGradient();
      if (batchSize > 1) {
        gradient.multiply(1.0 / batchSize);
      }
      log.stopTimer("compute_gradient_(serial)");

      log.startTimer("parameter_update");
      // Apply regularization and take a gradient step.
      double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(i + 2)) : stepSize;
      regularizer.apply(gradient, initialParameters, currentStepSize);

      // System.out.println(initialParameters);
      log.stopTimer("parameter_update");

      log.startTimer("compute_statistics");
      gradientL2 = gradient.getL2Norm();
      double objectiveValue = oracleResult.getObjectiveValue() / batchSize;
      exponentiallyWeightedUpdateNorm = (0.2) * gradientL2 * currentStepSize + (0.8 * exponentiallyWeightedUpdateNorm);
      exponentiallyWeightedObjectiveValue = objectiveValue + (0.9 * exponentiallyWeightedObjectiveValue);
      log.stopTimer("compute_statistics");

      log.logStatistic(i, "search errors", iterSearchErrors);
      log.logStatistic(i, "gradient l2 norm", gradientL2);
      log.logStatistic(i, "step size", currentStepSize);
      log.logStatistic(i, "objective value", objectiveValue);
      log.logStatistic(i, "objective value (moving avg.)", exponentiallyWeightedObjectiveValue / 9.0);
      log.logStatistic(i, "exponentially weighted update norm", exponentiallyWeightedUpdateNorm);
      log.notifyIterationEnd(i);
    }
    return initialParameters;
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
     * @param currentStepSize
     */
    public void apply(SufficientStatistics gradient, SufficientStatistics currentParameters,
        double currentStepSize);
  }

  /**
   * An L2 regularization penalty that is applied on random iterations. 
   * Regularization can be the most expensive part of training, since it
   * touches every parameter. Using a randomized regularized reduces the
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

    public void apply(SufficientStatistics gradient, SufficientStatistics currentParameters,
        double currentStepSize) {
      double rand = Pseudorandom.get().nextDouble();
      if (rand < frequency) {
        currentParameters.multiply(1 - (currentStepSize * l2Penalty * (1.0 / frequency)));
      }
      currentParameters.increment(gradient, currentStepSize);
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
        double currentStepSize) {
      currentParameters.increment(gradient, currentStepSize);
      currentParameters.softThreshold(currentStepSize * l1Penalty); 
    }
  }
}
