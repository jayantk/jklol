package com.jayantkrish.jklol.boost;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Functional gradient oracle for the loglikelihood loss function.
 *
 * @author jayantk
 */
public class LoglikelihoodBoostingOracle implements BoostingOracle<DynamicFactorGraph, 
Example<DynamicAssignment, DynamicAssignment>>{

  private final ParametricFactorGraphEnsemble family;
  private final MarginalCalculator marginalCalculator;
  
  public LoglikelihoodBoostingOracle(ParametricFactorGraphEnsemble family,
      MarginalCalculator marginalCalculator) {
    this.family = Preconditions.checkNotNull(family);
    this.marginalCalculator = Preconditions.checkNotNull(marginalCalculator);
  }
  
  public FunctionalGradient initializeFunctionalGradient() {
    return family.getNewFunctionalGradient();
  }

  @Override
  public DynamicFactorGraph instantiateModel(SufficientStatisticsEnsemble parameters) {
    return family.getModelFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(FunctionalGradient gradient, DynamicFactorGraph model,
      Example<DynamicAssignment, DynamicAssignment> example, LogFunction log) {
    // Instantiate any replicated factors, etc.
    log.startTimer("update_gradient/get_factor_graph_from_parameters");
    FactorGraph factorGraph = model.getFactorGraph(example.getInput());
    Assignment input = model.getVariables().toAssignment(example.getInput());
    Assignment observed = model.getVariables().toAssignment(
        example.getOutput().union(example.getInput()));
    log.stopTimer("update_gradient/get_factor_graph_from_parameters");

    log.startTimer("update_gradient/condition");
    // Compute the second term of the gradient, the unconditional expected
    // feature counts
    FactorGraph inputFactorGraph = factorGraph.conditional(input);
    log.stopTimer("update_gradient/condition");
    log.startTimer("update_gradient/input_marginal");
    // System.out.println("input factor graph:");
    // System.out.println(inputFactorGraph.getParameterDescription());
    MarginalSet inputMarginals = marginalCalculator.computeMarginals(inputFactorGraph);
    log.stopTimer("update_gradient/input_marginal");

    log.startTimer("update_gradient/output_marginal");
    // Compute the first term of the gradient, the model expectations
    // conditioned on the training example.
    FactorGraph outputFactorGraph = inputFactorGraph.conditional(observed
        .intersection(inputFactorGraph.getVariables()));
    // System.out.println("output factor graph:");
    // System.out.println(outputFactorGraph.getParameterDescription());
    MarginalSet outputMarginals = marginalCalculator.computeMarginals(
        outputFactorGraph);
    log.stopTimer("update_gradient/output_marginal");

    double inputPartitionFunction = inputMarginals.getPartitionFunction();
    double outputPartitionFunction = outputMarginals.getPartitionFunction();
    if (Double.isInfinite(inputPartitionFunction) || Double.isNaN(inputPartitionFunction)
        || Double.isInfinite(outputPartitionFunction) || Double.isNaN(outputPartitionFunction)) {
      // Search error from numerical issues.
      throw new ZeroProbabilityError();
    }
    
    family.incrementFunctionalGradient(gradient, inputMarginals, outputMarginals, 1.0);

    // Return the loglikelihood.
    return Math.log(outputMarginals.getPartitionFunction()) - 
        Math.log(inputMarginals.getPartitionFunction());
  }

  @Override
  public SufficientStatistics projectGradient(FunctionalGradient gradient) {
    return family.projectFunctionalGradient(gradient);
  }
}
