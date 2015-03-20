package com.jayantkrish.jklol.training;

import java.util.List;

import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Estimates the parameters of a {@link ParametricFactorGraph} using expectation
 * maximization.
 * 
 * @author jayantk
 */
public class EMTrainer extends AbstractTrainer
    <ParametricFactorGraph, Example<DynamicAssignment, DynamicAssignment>> {

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
    return train(bn, initialParameters, getOutputAssignments(trainingData, true));
  }

  /**
   * Trains {@code bn} by maximizing the marginal loglikelihood of
   * {@code trainingData}. Equivalent to calling {@code train} with examples
   * that only contain outputs.
   * 
   * @param bn
   * @param initialParameters
   * @param trainingData
   * @return
   */
  public SufficientStatistics train(ParametricFactorGraph bn,
      SufficientStatistics initialParameters, List<DynamicAssignment> trainingData) {
    
    ExpectationMaximization em = new ExpectationMaximization(numIterations, log);
    
    EmFactorGraphOracle oracle = new EmFactorGraphOracle(bn, marginalCalculator, initialParameters);
    return em.train(oracle, initialParameters, trainingData);
  }
}
