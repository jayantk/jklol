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
    this.regularizer = new L2Regularizer(0.0);
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
    return new StochasticGradientTrainer(numIterations, batchSize, stepSize, decayStepSize, new L2Regularizer(l2Penalty), log);
  }

  public static StochasticGradientTrainer createWithL1Regularization(int numIterations, int batchSize,
      double stepSize, boolean decayStepSize, double l1Penalty, LogFunction log) {
    return new StochasticGradientTrainer(numIterations, batchSize, stepSize, decayStepSize, new L1Regularizer(l1Penalty), log);
  }

  public <M, E> SufficientStatistics train(GradientOracle<M, E> oracle,
      SufficientStatistics initialParameters, Iterable<E> trainingData) {

    // cycledTrainingData loops indefinitely over the elements of trainingData.
    // This is desirable because we want batchSize examples but don't
    // particularly care where in trainingData they come from.
    Iterator<E> cycledTrainingData = Iterators.cycle(trainingData);

    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();

    int searchErrors = 0;
    double gradientL2 = 0.0;
    // This is an attempt at estimating how much the parameters are still
    // changing.
    // When it drops below some threshold, we'll say the algorithm has
    // converged.
    double exponentiallyWeightedUpdateNorm = stepSize;
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      searchErrors = 0;

      // Get the examples for this batch. Ideally, this would be a random
      // sample; however, deterministically iterating over the examples is
      // more efficient and is fairly close if the examples are provided in
      // random order.
      log.startTimer("factor_graph_from_parameters");
      List<E> batchData = getBatch(cycledTrainingData, batchSize);
      M currentModel = oracle.instantiateModel(initialParameters);
      log.stopTimer("factor_graph_from_parameters");

      log.startTimer("compute_gradient_(serial)");
      GradientEvaluation oracleResult = executor.mapReduce(batchData,
          new GradientMapper<M, E>(currentModel, oracle, log), new GradientReducer<M, E>(oracle, log));
      SufficientStatistics gradient = oracleResult.getGradient();
      gradient.multiply(1.0 / batchSize);
      log.stopTimer("compute_gradient_(serial)");

      log.startTimer("parameter_update");

      // System.out.println(currentStepSize);
      // Apply regularization and take a gradient step.
      double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(i + 2)) : stepSize;
      regularizer.apply(gradient, initialParameters, currentStepSize);

      // System.out.println(initialParameters);
      log.stopTimer("parameter_update");

      log.startTimer("compute_statistics");
      gradientL2 = gradient.getL2Norm();
      exponentiallyWeightedUpdateNorm = (0.2) * gradientL2 * currentStepSize + (0.8 * exponentiallyWeightedUpdateNorm);
      log.stopTimer("compute_statistics");

      log.logStatistic(i, "search errors", searchErrors);
      log.logStatistic(i, "gradient l2 norm", gradientL2);
      log.logStatistic(i, "step size", currentStepSize);
      log.logStatistic(i, "objective value", oracleResult.getObjectiveValue());
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
   * An L2 regularization penalty, i.e., a penalty on the sum of the squares of
   * the parameter weights.
   * 
   * @author jayantk
   */
  public static class L2Regularizer implements Regularizer {
    private final double l2Penalty;

    /**
     * Note that {@code l2Penalty} times the initial gradient step size should
     * be less than 1.0. If this value is greater than one, the gradient's
     * magnitude will be larger than the current parameter vector's magnitude,
     * causing training to bounce back and forth between positive and negative
     * parameter values for a while before converging.
     * 
     * @param l2Penalty
     */
    public L2Regularizer(double l2Penalty) {
      Preconditions.checkArgument(l2Penalty >= 0.0);
      this.l2Penalty = l2Penalty;
    }

    public void apply(SufficientStatistics gradient, SufficientStatistics currentParameters,
        double currentStepSize) {
      gradient.increment(currentParameters, -1.0 * l2Penalty);
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
