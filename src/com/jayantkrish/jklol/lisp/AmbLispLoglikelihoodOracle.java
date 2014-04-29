package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder.MarkedVars;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Loglikelihood objective function and gradient computation oracle
 * for Lisp programs. The function family being optimized is provided 
 * as a Lisp function that maps parameter vectors to probabilistic
 * functions. Training data consists of inputs and outputs for these 
 * returned probabilistic functions.
 * 
 * @author jayantk
 */
public class AmbLispLoglikelihoodOracle implements GradientOracle<AmbFunctionValue, Example<List<Object>, AmbFunctionValue>> {

  private final AmbFunctionValue family;
  private final Environment environment;
  private final ParameterSpec parameterSpec;

  private final MarginalCalculator marginalCalculator;

  public AmbLispLoglikelihoodOracle(AmbFunctionValue family, Environment environment,
      ParameterSpec parameterSpec, MarginalCalculator marginalCalculator) {
    this.family = Preconditions.checkNotNull(family);
    this.environment = Preconditions.checkNotNull(environment);
    this.parameterSpec = Preconditions.checkNotNull(parameterSpec);
    this.marginalCalculator = Preconditions.checkNotNull(marginalCalculator);
  }
  
  @Override
  public AmbFunctionValue instantiateModel(SufficientStatistics parameters) {
    ParametricBfgBuilder newBuilder = new ParametricBfgBuilder(true);
    Object value = family.apply(Arrays.asList(parameterSpec.wrap(parameters).toArgument()),
        environment, newBuilder);
    Preconditions.checkState(value instanceof AmbFunctionValue);    
    return (AmbFunctionValue) value;
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return parameterSpec.getNewParameters();
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient, SufficientStatistics currentParameters,
      AmbFunctionValue instantiatedModel, Example<List<Object>, AmbFunctionValue> example, LogFunction log) {
    // Evaluate the nondeterministic function on the current example to 
    // produce a factor graph for the distribution over outputs.
    ParametricBfgBuilder newBuilder = new ParametricBfgBuilder(true);
    Object inputApplicationResult = instantiatedModel.apply(example.getInput(), environment, newBuilder);
    ParametricFactorGraph pfg = newBuilder.buildNoBranching();

    Assignment inputAssignment = newBuilder.getAssignment();
    FactorGraph inputFactorGraph = pfg.getModelFromParameters(pfg.getNewSufficientStatistics())
        .conditional(DynamicAssignment.EMPTY).conditional(inputAssignment);

    // Compute the marginal distribution in this factor graph for
    // the first gradient term.
    MarginalSet inputMarginals = marginalCalculator.computeMarginals(inputFactorGraph);

    // Apply the output filter.
    AmbFunctionValue outputCondition = example.getOutput();
    outputCondition.apply(Arrays.asList(inputApplicationResult), environment, newBuilder);
    pfg = newBuilder.buildNoBranching();
    Assignment outputAssignment = newBuilder.getAssignment();
    FactorGraph outputFactorGraph = pfg.getModelFromParameters(pfg.getNewSufficientStatistics())
        .conditional(DynamicAssignment.EMPTY).conditional(outputAssignment);

    // Compute the marginal distribution given the constraint on the
    // output.
    MarginalSet outputMarginals = marginalCalculator.computeMarginals(outputFactorGraph);

    double inputLogPartitionFunction = inputMarginals.getLogPartitionFunction();
    double outputLogPartitionFunction = outputMarginals.getLogPartitionFunction();
    if (Double.isInfinite(inputLogPartitionFunction) || Double.isNaN(inputLogPartitionFunction)
        || Double.isInfinite(outputLogPartitionFunction) || Double.isNaN(outputLogPartitionFunction)) {
      // Search error from numerical issues.
      System.out.println("This search error: " + inputLogPartitionFunction + " " + outputLogPartitionFunction);
      throw new ZeroProbabilityError();
    }

    // Perform the gradient update. Note that this occurs after both marginal
    // calculations, since the marginal calculations may throw ZeroProbabilityErrors
    // (if inference in the graphical model fails.)
    log.startTimer("update_gradient/increment");
    ParameterSpec wrappedGradient = parameterSpec.wrap(gradient);
    ParameterSpec wrappedCurrentParameters = parameterSpec.wrap(currentParameters);
    incrementSufficientStatistics(newBuilder, wrappedGradient, wrappedCurrentParameters,
        inputMarginals, inputAssignment, -1.0);
    // System.out.println("=== input marginals ===");
    // System.out.println(inputMarginals);
    // System.out.println(gradient);
    // System.out.println("gradient l2: " + gradient.getL2Norm());

    incrementSufficientStatistics(newBuilder, wrappedGradient, wrappedCurrentParameters,
        outputMarginals, outputAssignment, 1.0);
    // System.out.println("=== output marginals ===");
    // System.out.println(outputMarginals);
    // System.out.println(gradient);
    // System.out.println("gradient l2: " + gradient.getL2Norm());
    log.stopTimer("update_gradient/increment");

    return outputLogPartitionFunction - inputLogPartitionFunction;
  }

  private static void incrementSufficientStatistics(ParametricBfgBuilder builder,
      ParameterSpec gradient, ParameterSpec currentParameters, MarginalSet marginals,
      Assignment assignment, double multiplier) {
    for (MarkedVars mark : builder.getMarkedVars()) {

      VariableNumMap vars = mark.getVars();
      ParametricFactor pf = mark.getFactor();
      SufficientStatistics factorGradient = gradient
          .getCurrentParametersByIds(mark.getParameterIds());
      SufficientStatistics factorCurrentParameters = currentParameters
          .getCurrentParametersByIds(mark.getParameterIds());
      VariableRelabeling relabeling = mark.getVarsToFactorRelabeling();

      // Figure out which variables have been conditioned on.
      Assignment factorAssignment = assignment.intersection(vars.getVariableNumsArray());
      VariableNumMap unconditionedVars = vars.removeAll(factorAssignment.getVariableNumsArray());

      Assignment relabeledAssignment = factorAssignment.mapVariables(
          relabeling.getVariableIndexReplacementMap());
      Factor marginal = marginals.getMarginal(unconditionedVars).relabelVariables(relabeling);
      double partitionFunction = marginal.getTotalUnnormalizedProbability();

      pf.incrementSufficientStatisticsFromMarginal(factorGradient, factorCurrentParameters,
          marginal, relabeledAssignment, multiplier, partitionFunction);
    }
  }
}
