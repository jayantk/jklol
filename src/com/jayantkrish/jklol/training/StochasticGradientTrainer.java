package com.jayantkrish.jklol.training;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;

/**
 * An implementation of stochastic (sub)gradient ascent that can optimize any
 * function given by a {@link GradientOracle}.
 * 
 * @author jayantk
 * @param <T>
 */
public class StochasticGradientTrainer {

  private final int numIterations;
  private final int batchSize;
  protected final LogFunction log;

  private final double stepSize;
  private final boolean decayStepSize;
  private final double l2Regularization;

  public StochasticGradientTrainer(int numIterations, int batchSize,
      double stepSize, boolean decayStepSize, double l2Regularization, LogFunction log) {
    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.log = (log != null) ? log : new NullLogFunction();

    Preconditions.checkArgument(l2Regularization >= 0.0);
    // High regularization parameters mean the gradient's magnitude may be
    // larger than the parameter vector's magnitude, causing the training to
    // bounce back and forth between positive and negative parameter values for
    // a while before converging.
    Preconditions.checkArgument(l2Regularization * stepSize <= 1);
    this.stepSize = stepSize;
    this.decayStepSize = decayStepSize;
    this.l2Regularization = l2Regularization;
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
      SufficientStatistics gradient = executor.mapReduce(batchData,
          new GradientMapper<M, E>(currentModel, oracle), new GradientReducer<M, E>(oracle));
      log.stopTimer("compute_gradient_(serial)");

      log.startTimer("parameter_update");

      double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(i + 2)) : stepSize;

      // System.out.println(currentStepSize);
      // Apply L2 regularization.
      initialParameters.multiply(1.0 - (currentStepSize * l2Regularization));
      initialParameters.increment(gradient, currentStepSize);
      // System.out.println(initialParameters);
      log.stopTimer("parameter_update");

      log.startTimer("compute_statistics");
      gradientL2 = gradient.getL2Norm();
      exponentiallyWeightedUpdateNorm = (0.2) * gradientL2 * currentStepSize + (0.8 * exponentiallyWeightedUpdateNorm);
      log.stopTimer("compute_statistics");

      log.logStatistic(i, "search errors", searchErrors);
      log.logStatistic(i, "gradient l2 norm", gradientL2);
      log.logStatistic(i, "step size", currentStepSize);
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
   * Mapper for parallelizing gradient computation for multiple examples.
   * 
   * @author jayantk
   */
  private class GradientMapper<M, E> extends Mapper<E, SufficientStatistics> {
    private final M instantiatedModel;
    private final GradientOracle<M, E> oracle;

    public GradientMapper(M instantiatedModel, GradientOracle<M, E> oracle) {
      this.instantiatedModel = instantiatedModel;
      this.oracle = oracle;
    }

    @Override
    public SufficientStatistics map(E item) {
      log.startTimer("compute_gradient_(parallel)");
      SufficientStatistics gradient = oracle.initializeGradient();
      try {
        oracle.accumulateGradient(gradient, instantiatedModel, item, log);
      } catch (ZeroProbabilityError e) {
        // Ignore the example, returning the zero vector.
      }
      log.stopTimer("compute_gradient_(parallel)");
      return gradient;
    }
  }

  /**
   * Reducer for accumulating gradients from multiple examples.
   * 
   * @author jayantk
   */
  private class GradientReducer<M, E> extends SimpleReducer<SufficientStatistics> {

    private final GradientOracle<M, E> oracle;

    public GradientReducer(GradientOracle<M, E> oracle) {
      this.oracle = oracle;
    }

    @Override
    public SufficientStatistics getInitialValue() {
      return oracle.initializeGradient();
    }

    @Override
    public SufficientStatistics reduce(SufficientStatistics item, SufficientStatistics accumulated) {
      log.startTimer("accumulate_gradient");
      accumulated.increment(item, 1.0);
      log.stopTimer("accumulate_gradient");
      return accumulated;
    }
  }
}
