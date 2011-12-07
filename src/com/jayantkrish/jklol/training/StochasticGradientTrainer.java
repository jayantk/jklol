package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Trains the weights of a factor graph using stochastic gradient descent.
 */
public class StochasticGradientTrainer {

  private MarginalCalculator marginalCalculator;
  private int numIterations;

  public StochasticGradientTrainer(MarginalCalculator inferenceEngine, int numIterations) {
    this.marginalCalculator = inferenceEngine;
    this.numIterations = numIterations;
  }

  public SufficientStatistics train(ParametricFactorGraph logLinearModel, SufficientStatistics initialParameters,
      List<Example<Assignment, Assignment>> trainingData) {
    Collections.shuffle(trainingData);
    for (int i = 0; i < numIterations; i++) {
      for (Example<Assignment, Assignment> trainingExample : trainingData) {
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
      ParametricFactorGraph logLinearModel, Example<Assignment, Assignment> trainingExample) {
    FactorGraph factorGraph = logLinearModel.getFactorGraphFromParameters(parameters);

    // The gradient is the conditional expected counts minus the unconditional
    // expected counts
    SufficientStatistics gradient = logLinearModel.getNewSufficientStatistics(); 

    // Input is always conditioned on, and the difference between input and output
    // is what's predicted.
    Assignment input = trainingExample.getInput();
    Assignment output = trainingExample.getOutput().union(input);
    
    // Compute the second term of the gradient, the unconditional expected
    // feature counts
    FactorGraph inputFactorGraph = factorGraph.conditional(input);
    MarginalSet inputMarginals = marginalCalculator.computeMarginals(inputFactorGraph);
    logLinearModel.incrementSufficientStatistics(
        gradient, inputMarginals, -1.0);

    // Compute the first term of the gradient, the model expectations
    // conditioned on the training example.
    FactorGraph outputFactorGraph = factorGraph.conditional(output);
    MarginalSet outputMarginals = marginalCalculator.computeMarginals(
        outputFactorGraph);
    logLinearModel.incrementSufficientStatistics(gradient, outputMarginals, 1.0);
    
    return gradient;
  }
}