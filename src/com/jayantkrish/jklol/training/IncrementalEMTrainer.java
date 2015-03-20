package com.jayantkrish.jklol.training;

import java.util.List;

import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Train the weights of a factor graph using incremental EM. (Incremental EM is
 * an online variant of EM.)
 */
public class IncrementalEmTrainer extends AbstractTrainer
<ParametricFactorGraph, Example<DynamicAssignment, DynamicAssignment>> {

  private MarginalCalculator inferenceEngine;
  private int numIterations;
  private LogFunction log;

  public IncrementalEmTrainer(int numIterations, MarginalCalculator inferenceEngine) {
    this.numIterations = numIterations;
    this.inferenceEngine = inferenceEngine;
    this.log = new NullLogFunction();
  }

  public IncrementalEmTrainer(int numIterations, MarginalCalculator inferenceEngine, LogFunction log) {
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
  @Override
  public SufficientStatistics train(ParametricFactorGraph bn,
      SufficientStatistics initialParameters, 
      Iterable<Example<DynamicAssignment, DynamicAssignment>> trainingDataExamples) {

    List<DynamicAssignment> trainingData = getOutputAssignments(trainingDataExamples, true);
    SufficientStatistics[] previousIterationStatistics = new SufficientStatistics[trainingData.size()];

    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      for (int j = 0; j < trainingData.size(); j++) {
        if (i > 0) {
          // Subtract out old statistics if they exist.
          initialParameters.increment(previousIterationStatistics[j], -1.0);
        }

        // Get the current training data point and the most recent factor graph
        // based on the current iteration.
        DynamicAssignment dynamicExample = trainingData.get(j);
        DynamicFactorGraph dynamicFactorGraph = bn.getModelFromParameters(initialParameters);
        FactorGraph currentFactorGraph = dynamicFactorGraph.getFactorGraph(dynamicExample);
        Assignment trainingExample = dynamicFactorGraph.getVariables().toAssignment(dynamicExample);
        log.log(i, j, trainingExample, currentFactorGraph);

        // Compute the marginal distribution of currentFactorGraph conditioned on
        // the current training example.
        FactorGraph conditionalFactorGraph = currentFactorGraph.conditional(trainingExample);
        MarginalSet marginals = inferenceEngine.computeMarginals(conditionalFactorGraph);
            
        // Update new sufficient statistics
        SufficientStatistics exampleStatistics = bn.getNewSufficientStatistics(); 
        bn.incrementSufficientStatistics(exampleStatistics, initialParameters, marginals, 1.0);
        previousIterationStatistics[j] = exampleStatistics;
        initialParameters.increment(exampleStatistics, 1.0);
      }
      log.notifyIterationEnd(i);
    }
    
    return initialParameters;
  }
}