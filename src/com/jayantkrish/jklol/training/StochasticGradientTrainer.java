package com.jayantkrish.jklol.training;

import com.google.common.base.Preconditions;
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
 * Trains the weights of a factor graph using stochastic gradient descent.
 */
public class StochasticGradientTrainer extends AbstractTrainer {

  private final MarginalCalculator marginalCalculator;
  private final int numIterations;
  private final LogFunction log;

  public StochasticGradientTrainer(MarginalCalculator inferenceEngine, int numIterations,
      LogFunction log) {
    this.marginalCalculator = Preconditions.checkNotNull(inferenceEngine);
    this.numIterations = numIterations;
    this.log = Preconditions.checkNotNull(log);
  }

  public SufficientStatistics train(ParametricFactorGraph logLinearModel, SufficientStatistics initialParameters,
      Iterable<Example<DynamicAssignment, DynamicAssignment>> trainingData) {

    int iterationCount = 0;
    for (int i = 0; i < numIterations; i++) {
      for (Example<DynamicAssignment, DynamicAssignment> trainingExample : trainingData) {
        log.notifyIterationStart(iterationCount);

        log.startTimer("update_gradient");
        SufficientStatistics gradient = computeGradient(initialParameters, logLinearModel, trainingExample);
        log.stopTimer("update_gradient");
        
        log.startTimer("parameter_update");
        initialParameters.increment(gradient, 1.0 / Math.sqrt(i + 2));
        log.stopTimer("parameter_update");

        log.notifyIterationEnd(iterationCount);
        iterationCount++;
      }
    }
    return initialParameters;
  }

  /*
   * Computes and returns an estimate of the gradient at {@code parameters}
   * based on {@code trainingExample}.
   */
  private SufficientStatistics computeGradient(SufficientStatistics parameters,
      ParametricFactorGraph logLinearModel, Example<DynamicAssignment, DynamicAssignment> dynamicExample) {
    // Instantiate any replicated factors, etc.
    DynamicFactorGraph dynamicFactorGraph = logLinearModel.getFactorGraphFromParameters(parameters);
    FactorGraph factorGraph = dynamicFactorGraph.getFactorGraph(dynamicExample.getInput());
    Assignment input = dynamicFactorGraph.getVariables().toAssignment(dynamicExample.getInput());
    Assignment observed = dynamicFactorGraph.getVariables().toAssignment(
        dynamicExample.getOutput().union(dynamicExample.getInput()));
    
    log.log(input, factorGraph);
    log.log(observed, factorGraph);
    
    // The gradient is the conditional expected counts minus the unconditional
    // expected counts
    SufficientStatistics gradient = logLinearModel.getNewSufficientStatistics();
    
    // Compute the second term of the gradient, the unconditional expected
    // feature counts
    FactorGraph inputFactorGraph = factorGraph.conditional(input);
    MarginalSet inputMarginals = marginalCalculator.computeMarginals(inputFactorGraph);
    logLinearModel.incrementSufficientStatistics(gradient, inputMarginals, -1.0);

    // Compute the first term of the gradient, the model expectations
    // conditioned on the training example.
    FactorGraph outputFactorGraph = factorGraph.conditional(observed); 
    MarginalSet outputMarginals = marginalCalculator.computeMarginals(
        outputFactorGraph);
    logLinearModel.incrementSufficientStatistics(gradient, outputMarginals, 1.0);
    
    return gradient;
  }
}