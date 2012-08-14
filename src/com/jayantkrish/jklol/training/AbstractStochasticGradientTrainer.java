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
 * An abstract implementation of stochastic gradient descent that contains the
 * abstract gradient descent structure but defers gradient computation to
 * subclasses. See {@link StochasticGradientTrainer} for example usage.
 * 
 * @author jayantk
 * @param <T>
 */
public abstract class AbstractStochasticGradientTrainer<T, U, E> extends AbstractTrainer<T, E> {

  private final int numIterations;
  private final int batchSize;
  protected final LogFunction log;

  private final double stepSize;
  private final boolean decayStepSize;
  private final double l2Regularization;

  public AbstractStochasticGradientTrainer(int numIterations, int batchSize,
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

  public SufficientStatistics train(T modelFamily, SufficientStatistics initialParameters,
      Iterable<E> trainingData) {

    // cycledTrainingData loops indefinitely over the elements of trainingData.
    // This is desirable because we want batchSize examples but don't
    // particularly care where in trainingData they come from.
    Iterator<E> cycledTrainingData = Iterators.cycle(trainingData);

    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();

    int searchErrors = 0;
    double gradientL2 = 0.0;
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      searchErrors = 0;

      // Get the examples for this batch. Ideally, this would be a random
      // sample; however, deterministically iterating over the examples is
      // more efficient and is fairly close if the examples are provided in
      // random order.
      log.startTimer("factor_graph_from_parameters");
      List<E> batchData = getBatch(cycledTrainingData, batchSize);
      U currentModel = instantiateModel(modelFamily, initialParameters);
      log.stopTimer("factor_graph_from_parameters");
      
      SufficientStatistics gradient = executor.mapReduce(batchData, 
          new GradientMapper(currentModel, modelFamily), new GradientReducer(modelFamily));

      gradientL2 = gradient.getL2Norm();

      log.startTimer("parameter_update");
      double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(i + 2)) : stepSize;

      // System.out.println(currentStepSize);
      // Apply L2 regularization.
      initialParameters.multiply(1.0 - (currentStepSize * l2Regularization));
      initialParameters.increment(gradient, currentStepSize);
      // System.out.println(initialParameters);
      log.stopTimer("parameter_update");

      log.logStatistic(i, "search errors", Integer.toString(searchErrors));
      log.logStatistic(i, "gradient l2 norm", Double.toString(gradientL2));
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

  protected abstract SufficientStatistics initializeGradient(T model);

  protected abstract U instantiateModel(T model, SufficientStatistics parameters);

  /**
   * Computes and returns an estimate of the gradient of {@code modelFamily} at
   * {@code instantiatedModel} based on {@code example}. {@code gradient} should
   * be incremented with the resulting value.
   */
  protected abstract void accumulateGradient(SufficientStatistics gradient,
      U instantiatedModel, T modelFamily, E example);

  /**
   * Mapper for parallelizing gradient computation for multiple examples.
   * 
   * @author jayantk
   */
  private class GradientMapper extends Mapper<E, SufficientStatistics> {
    private final U instantiatedModel;
    private final T modelFamily;

    public GradientMapper(U instantiatedModel, T modelFamily) {
      this.instantiatedModel = instantiatedModel;
      this.modelFamily = modelFamily;
    }

    @Override
    public SufficientStatistics map(E item) {
      log.startTimer("compute_gradient");
      SufficientStatistics gradient = initializeGradient(modelFamily);
      try {
        accumulateGradient(gradient, instantiatedModel, modelFamily, item);
      } catch (ZeroProbabilityError e) {
        // Ignore the example, returning the zero vector.
      }
      log.stopTimer("compute_gradient");
      return gradient;
    }
  }

  /**
   * Reducer for accumulating gradients from multiple examples.
   * 
   * @author jayantk
   */
  private class GradientReducer extends SimpleReducer<SufficientStatistics> {
    
    private final T modelFamily;
    
    public GradientReducer(T modelFamily) {
      this.modelFamily = modelFamily;
    }

    @Override
    public SufficientStatistics getInitialValue() {
      return initializeGradient(modelFamily);
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
