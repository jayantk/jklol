package com.jayantkrish.jklol.training;

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

  private MarginalCalculator marginalCalculator;
  private int numIterations;

  public StochasticGradientTrainer(MarginalCalculator inferenceEngine, int numIterations) {
    this.marginalCalculator = inferenceEngine;
    this.numIterations = numIterations;
  }

  public SufficientStatistics train(ParametricFactorGraph logLinearModel, SufficientStatistics initialParameters,
      Iterable<Example<DynamicAssignment, DynamicAssignment>> trainingData) {

    for (int i = 0; i < numIterations; i++) {
      for (Example<DynamicAssignment, DynamicAssignment> trainingExample : trainingData) {
        SufficientStatistics gradient = computeGradient(initialParameters, logLinearModel, trainingExample);
        // TODO: decay the gradient increment amount as (say)
        // sqrt(numIterations)
        initialParameters.increment(gradient, 1.0);
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
    Assignment output = dynamicFactorGraph.getVariables().toAssignment(dynamicExample.getOutput());
    
    // The gradient is the conditional expected counts minus the unconditional
    // expected counts
    SufficientStatistics gradient = logLinearModel.getNewSufficientStatistics();
    
    // Compute the second term of the gradient, the unconditional expected
    // feature counts
    FactorGraph inputFactorGraph = factorGraph.conditional(input);
    MarginalSet inputMarginals = marginalCalculator.computeMarginals(inputFactorGraph);
    logLinearModel.incrementSufficientStatistics(
        gradient, inputMarginals, -1.0);

    // Compute the first term of the gradient, the model expectations
    // conditioned on the training example.
    FactorGraph outputFactorGraph = factorGraph.conditional(input.union(output)); 
    MarginalSet outputMarginals = marginalCalculator.computeMarginals(
        outputFactorGraph);
    logLinearModel.incrementSufficientStatistics(gradient, outputMarginals, 1.0);
    
    return gradient;
  }
}