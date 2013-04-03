package com.jayantkrish.jklol.boost;

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
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;

/**
 * Trains a model using boosting. This implementation is based on the
 * regression reduction of boosting to functional gradient ascent.
 * 
 * @author jayantk
 */
public class FunctionalGradientAscent {

  private final int numIterations;
  private final int batchSize;

  private final double stepSize;
  private final boolean decayStepSize;

  private final LogFunction log;

  /**
   * Functional gradient ascent.
   * 
   * @param numIterations
   * @param batchSize
   * @param stepSize
   * @param decayStepSize
   * @param log
   */
  public FunctionalGradientAscent(int numIterations, int batchSize,
      double stepSize, boolean decayStepSize, LogFunction log) {
    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.log = (log != null) ? log : new NullLogFunction();

    this.stepSize = stepSize;
    this.decayStepSize = decayStepSize;
  }

  public <M, E, T extends E> SufficientStatisticsEnsemble train(BoostingOracle<M, E> oracle,
      SufficientStatisticsEnsemble initialParameters, Iterable<T> trainingData) {

    // cycledTrainingData loops indefinitely over the elements of
    // trainingData. This is desirable because we want batchSize
    // examples but don't particularly care where in trainingData they
    // come from.
    Iterator<T> cycledTrainingData = Iterators.cycle(trainingData);
    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);

      // Get the examples for this batch. Ideally, this would be a
      // random sample; however, deterministically iterating over the
      // examples is more efficient and is fairly close if the
      // examples are provided in random order.
      log.startTimer("factor_graph_from_parameters");
      List<T> batchData = getBatch(cycledTrainingData, batchSize);
      M currentModel = oracle.instantiateModel(initialParameters);
      log.stopTimer("factor_graph_from_parameters");

      log.startTimer("compute_gradient_(serial)");
      BoostingGradientEvaluation gradientEvaluation = executor.mapReduce(batchData,
          new BoostingGradientMapper<M, T>(currentModel, oracle, log),
          new BoostingGradientReducer<M, T>(oracle, log));
      log.logStatistic(i, "objective value", gradientEvaluation.getObjectiveValue() / batchSize);
      log.logStatistic(i, "search errors", gradientEvaluation.getSearchErrors());
      FunctionalGradient functionalGradient = gradientEvaluation.getGradient();
      log.stopTimer("compute_gradient_(serial)");

      log.startTimer("project_gradient");
      SufficientStatistics projectedGradient = oracle.projectGradient(functionalGradient);
      log.stopTimer("project_gradient");

      log.startTimer("parameter_update");
      // Take a gradient step.
      double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(i + 1)) : stepSize;
      log.logStatistic(i, "step size", currentStepSize);
      initialParameters.addStatistics(projectedGradient, currentStepSize);
      log.stopTimer("parameter_update");

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

  private static class BoostingGradientEvaluation {
    private FunctionalGradient gradient;
    private double objectiveValue;
    private int searchErrors;

    public BoostingGradientEvaluation(FunctionalGradient gradient, double objectiveValue, int searchErrors) {
      this.gradient = gradient;
      this.objectiveValue = objectiveValue;
      this.searchErrors = searchErrors;
    }

    public FunctionalGradient getGradient() {
      return gradient;
    }

    public double getObjectiveValue() {
      return objectiveValue;
    }

    public int getSearchErrors() {
      return searchErrors;
    }
    
    public void increment(BoostingGradientEvaluation evaluation) {
      gradient.combineExamples(evaluation.getGradient());
      objectiveValue += evaluation.getObjectiveValue();
      searchErrors += evaluation.getSearchErrors();
    }
  }

  private static class BoostingGradientMapper<M, E> extends Mapper<E, BoostingGradientEvaluation> {
    private final M instantiatedModel;
    private final BoostingOracle<M, ? super E> oracle;
    private final LogFunction log;

    public BoostingGradientMapper(M instantiatedModel, BoostingOracle<M, ? super E> oracle,
        LogFunction log) {
      this.instantiatedModel = Preconditions.checkNotNull(instantiatedModel);
      this.oracle = Preconditions.checkNotNull(oracle);
      this.log = Preconditions.checkNotNull(log);
    }

    @Override
    public BoostingGradientEvaluation map(E item) {
      log.startTimer("compute_gradient_(parallel)");
      double objective = 0.0;
      int searchErrors = 0;
      FunctionalGradient gradient = oracle.initializeFunctionalGradient();
      try {
        objective += oracle.accumulateGradient(gradient, instantiatedModel, item, log);
      } catch (ZeroProbabilityError e) {
        // Ignore the example.
        searchErrors = 1;
      }
      log.stopTimer("compute_gradient_(parallel)");
      return new BoostingGradientEvaluation(gradient, objective, searchErrors);
    }
  }

  private static class BoostingGradientReducer<M, E> extends SimpleReducer<BoostingGradientEvaluation> {
    private final BoostingOracle<M, ? super E> oracle;
    private final LogFunction log;

    public BoostingGradientReducer(BoostingOracle<M, ? super E> oracle, LogFunction log) {
      this.oracle = Preconditions.checkNotNull(oracle);
      this.log = Preconditions.checkNotNull(log);
    }

    @Override
    public BoostingGradientEvaluation getInitialValue() {
      FunctionalGradient gradient = oracle.initializeFunctionalGradient();
      return new BoostingGradientEvaluation(gradient, 0.0, 0);
    }

    @Override
    public BoostingGradientEvaluation reduce(BoostingGradientEvaluation item,
        BoostingGradientEvaluation accumulator) {
      log.startTimer("accumulate_gradient");
      accumulator.increment(item);
      log.stopTimer("accumulate_gradient");
      return accumulator;
    }
  }
}
