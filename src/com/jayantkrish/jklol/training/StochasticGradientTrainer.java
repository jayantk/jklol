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
public class StochasticGradientTrainer extends AbstractStochasticGradientTrainer<ParametricFactorGraph, DynamicFactorGraph> {

  private final MarginalCalculator marginalCalculator;

  /**
   * @param inferenceEngine
   * @param numIterations
   * @param log
   * @param stepSize
   * @param l2Regularization valid only if {@code 0 <= l2Regularization < 1.0}.
   */
  public StochasticGradientTrainer(MarginalCalculator inferenceEngine, int numIterations,
      int batchSize, LogFunction log, double stepSize, double l2Regularization) {
    super(numIterations, batchSize, log, stepSize, l2Regularization);
    this.marginalCalculator = Preconditions.checkNotNull(inferenceEngine);
  }
  
  @Override
  protected SufficientStatistics initializeGradient(ParametricFactorGraph model) {
    return model.getNewSufficientStatistics();
  }
  
  @Override
  protected DynamicFactorGraph instantiateModel(ParametricFactorGraph modelFamily, 
      SufficientStatistics parameters) {
    return modelFamily.getFactorGraphFromParameters(parameters);
  }

  @Override
  protected void accumulateGradient(SufficientStatistics gradient, DynamicFactorGraph dynamicFactorGraph,
      ParametricFactorGraph logLinearModel, Example<DynamicAssignment, DynamicAssignment> dynamicExample) {
    // Instantiate any replicated factors, etc.
    log.startTimer("update_gradient/get_factor_graph_from_parameters");
    FactorGraph factorGraph = dynamicFactorGraph.getFactorGraph(dynamicExample.getInput());
    Assignment input = dynamicFactorGraph.getVariables().toAssignment(dynamicExample.getInput());
    Assignment observed = dynamicFactorGraph.getVariables().toAssignment(
        dynamicExample.getOutput().union(dynamicExample.getInput()));

    log.stopTimer("update_gradient/get_factor_graph_from_parameters");
    log.log(input, factorGraph);
    log.log(observed, factorGraph);

    log.startTimer("update_gradient/input_marginal");
    // Compute the second term of the gradient, the unconditional expected
    // feature counts
    FactorGraph inputFactorGraph = factorGraph.conditional(input);
    // System.out.println("input factor graph:");
    // System.out.println(inputFactorGraph.getParameterDescription());

    MarginalSet inputMarginals = marginalCalculator.computeMarginals(inputFactorGraph);
    log.stopTimer("update_gradient/input_marginal");

    log.startTimer("update_gradient/output_marginal");
    // Compute the first term of the gradient, the model expectations
    // conditioned on the training example.
    FactorGraph outputFactorGraph = factorGraph.conditional(observed);
    // System.out.println("output factor graph:");
    // System.out.println(outputFactorGraph.getParameterDescription());
    MarginalSet outputMarginals = marginalCalculator.computeMarginals(
        outputFactorGraph);
    log.stopTimer("update_gradient/output_marginal");

    // Perform the gradient update. Note that this occurs after both marginal
    // calculations, since the marginal calculations may throw ZeroProbabilityErrors
    // (if inference in the graphical model fails.)
    log.startTimer("update_gradient/increment");
    logLinearModel.incrementSufficientStatistics(gradient, inputMarginals, -1.0);
    // System.out.println("=== input marginals ===");
    // System.out.println(inputMarginals);
    logLinearModel.incrementSufficientStatistics(gradient, outputMarginals, 1.0);
    // System.out.println("=== output marginals ===");
    // System.out.println(outputMarginals);
    log.stopTimer("update_gradient/increment");
  }
}