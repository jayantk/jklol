package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Train the weights of a factor graph using incremental EM. (Incremental EM is
 * an online variant of EM.)
 */
public class IncrementalEMTrainer {

  private MarginalCalculator inferenceEngine;
  private int numIterations;
  private LogFunction log;

  public IncrementalEMTrainer(int numIterations, MarginalCalculator inferenceEngine) {
    this.numIterations = numIterations;
    this.inferenceEngine = inferenceEngine;
    this.log = new NullLogFunction();
  }

  public IncrementalEMTrainer(int numIterations, MarginalCalculator inferenceEngine, LogFunction log) {
    this.numIterations = numIterations;
    this.inferenceEngine = inferenceEngine;
    this.log = log;
  }

  /**
   * Trains {@code bn} using {@code trainingData}, returning the resulting
   * parameters. These parameters maximize the marginal likelihood of the
   * observed data, assuming that training is run for a sufficient number of
   * iterations, etc.
   * 
   * <p>
   * {@code initialParameters} are used as the starting point for training. A
   * reasonable starting point is the uniform distribution (add-one smoothing).
   * These parameters can be retrieved using
   * {@code bn.getNewSufficientStatistics().increment(1)}. Incremental EM will
   * retain the smoothing throughout the training process. This method may
   * modify {@code initialParameters}.
   * 
   * @param bn
   * @param initialParameters
   * @param trainingData
   */
  public SufficientStatistics train(ParametricFactorGraph bn,
      SufficientStatistics initialParameters, List<Assignment> trainingData) {
    SufficientStatistics[] previousIterationStatistics = new SufficientStatistics[trainingData.size()];

    Collections.shuffle(trainingData);
    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      for (int j = 0; j < trainingData.size(); j++) {
        if (i > 0) {
          // Subtract out old statistics if they exist.
          initialParameters.increment(previousIterationStatistics[j], -1.0);
        }

        // Get the current training data point and the most recent factor graph
        // based on the current iteration.
        Assignment trainingExample = trainingData.get(j);
        FactorGraph currentFactorGraph = bn.getFactorGraphFromParameters(initialParameters);
        log.log(i, j, trainingExample, currentFactorGraph);

        // Update new sufficient statistics
        MarginalSet marginals = inferenceEngine.computeMarginals(currentFactorGraph, trainingExample);
        SufficientStatistics exampleStatistics = bn.computeSufficientStatistics(marginals, 1.0);
        previousIterationStatistics[j] = exampleStatistics;
        initialParameters.increment(exampleStatistics, 1.0);
      }
      log.notifyIterationEnd(i);
    }
    
    return initialParameters;
  }
}