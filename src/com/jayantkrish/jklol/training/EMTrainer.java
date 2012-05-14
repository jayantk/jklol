package com.jayantkrish.jklol.training;

import java.util.List;

import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;

/**
 * Estimates the parameters of a {@link ParametricFactorGraph} using expectation
 * maximization.
 * 
 * @author jayantk
 */
public class EMTrainer extends AbstractTrainer<ParametricFactorGraph> {

  private final int numIterations;
  private final MarginalCalculator marginalCalculator;

  private final LogFunction log;

  /**
   * Creates an {@code EMTrainer} that performs {@code numIterations} of EM.
   * E-steps are executed in parallel using the global mapreduce executor, and
   * the corresponding marginals (expectations) are computed by
   * {@code marginalCalculator}.
   * 
   * @param numIterations
   * @param statisticsCalculator
   * @param log
   */
  public EMTrainer(int numIterations, MarginalCalculator marginalCalculator,
      LogFunction log) {
    this.numIterations = numIterations;
    this.marginalCalculator = marginalCalculator;

    if (log != null) {
      this.log = log;
    } else {
      this.log = new NullLogFunction();
    }
  }

  @Override
  public SufficientStatistics train(ParametricFactorGraph bn,
      SufficientStatistics initialParameters,
      Iterable<Example<DynamicAssignment, DynamicAssignment>> trainingData) {
    // This class always performs joint estimation, which corresponds to the
    // outputs of trainingData.
    List<DynamicAssignment> trainingDataList = getOutputAssignments(trainingData, true);

    SufficientStatistics oldStatistics = null;
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      // E-step: compute the expected values of the hidden variables given the
      // current set of parameters.
      DynamicFactorGraph factorGraph = bn.getFactorGraphFromParameters(initialParameters);
      SufficientStatisticsBatch batchStatistics = MapReduceConfiguration.getMapReduceExecutor()
          .mapReduce(trainingDataList,
              new SufficientStatisticsMapper(factorGraph, marginalCalculator, log),
              new SufficientStatisticsReducer(bn));
      SufficientStatistics statistics = batchStatistics.getStatistics();
      log.logStatistic(i, "average loglikelihood",
          Double.toString(batchStatistics.getLoglikelihood() / batchStatistics.getNumExamples()));

      // M-step: maximize the parameters.
      // Due to a poor design decision I made in an experiment using this class,
      // initialParameters must always contain the current parameters of the
      // model.
      if (oldStatistics != null) {
        initialParameters.increment(oldStatistics, -1.0);
      }
      initialParameters.increment(statistics, 1.0);
      oldStatistics = statistics;

      log.notifyIterationEnd(i);
    }
    return initialParameters;
  }
}
