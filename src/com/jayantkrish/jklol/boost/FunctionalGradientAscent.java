package com.jayantkrish.jklol.boost;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
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

  public <M, F, E, T extends E> SufficientStatisticsEnsemble train(BoostingOracle<M, F, E> oracle,
      SufficientStatisticsEnsemble initialParameters, Iterable<T> trainingData) {

    // cycledTrainingData loops indefinitely over the elements of
    // trainingData.
    // This is desirable because we want batchSize examples but don't
    // particularly care where in trainingData they come from.
    Iterator<T> cycledTrainingData = Iterators.cycle(trainingData);

    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);

      // Get the examples for this batch. Ideally, this would be a
      // random
      // sample; however, deterministically iterating over the
      // examples is
      // more efficient and is fairly close if the examples are
      // provided in
      // random order.
      log.startTimer("factor_graph_from_parameters");
      List<T> batchData = getBatch(cycledTrainingData, batchSize);
      M currentModel = oracle.instantiateModel(initialParameters);
      log.stopTimer("factor_graph_from_parameters");

      log.startTimer("compute_gradient_(serial)");
      F functionalGradient = oracle.initializeFunctionalGradient();
      for (T trainingDatum : batchData) {
        oracle.accumulateGradient(functionalGradient, currentModel, trainingDatum, log);
      }
      log.stopTimer("compute_gradient_(serial)");

      log.startTimer("parameter_update");
      // Apply regularization and take a gradient step.
      double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(i + 2)) : stepSize;

      // TODO: take gradient step.
      initialParameters.addStatistics(oracle.projectGradient(functionalGradient), stepSize);

      // System.out.println(initialParameters);
      log.stopTimer("parameter_update");
      log.logStatistic(i, "step size", currentStepSize);
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
}
