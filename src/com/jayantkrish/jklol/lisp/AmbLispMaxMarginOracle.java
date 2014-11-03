package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder.MarkedVars;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.Assignment;

public class AmbLispMaxMarginOracle implements GradientOracle<AmbFunctionValue,
Example<List<Object>, Example<AmbFunctionValue,AmbFunctionValue>>> {

  private final AmbFunctionValue family;
  private final Environment environment;
  private final ParameterSpec parameterSpec;

  private final MarginalCalculator marginalCalculator;

  public AmbLispMaxMarginOracle(AmbFunctionValue family, Environment environment,
      ParameterSpec parameterSpec, MarginalCalculator marginalCalculator) {
    this.family = Preconditions.checkNotNull(family);
    this.environment = Preconditions.checkNotNull(environment);
    this.parameterSpec = Preconditions.checkNotNull(parameterSpec);
    this.marginalCalculator = Preconditions.checkNotNull(marginalCalculator);
  }
  
  @Override
  public AmbFunctionValue instantiateModel(SufficientStatistics parameters) {
    ParametricBfgBuilder newBuilder = new ParametricBfgBuilder(true);
    Object value = family.apply(Arrays.<Object>asList(new SpecAndParameters(parameterSpec, parameters)),
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
      AmbFunctionValue instantiatedModel, Example<List<Object>,
      Example<AmbFunctionValue, AmbFunctionValue>> example, LogFunction log) {
    List<Object> input = example.getInput();
    AmbFunctionValue costFunction = example.getOutput().getInput();
    AmbFunctionValue outputFunction = example.getOutput().getOutput();

    // Evaluate the nondeterministic function on the input value 
    // then augment the distribution with the costs. 
    ParametricBfgBuilder inputBuilder = new ParametricBfgBuilder(true);
    Object inputApplicationResult = instantiatedModel.apply(input, environment, inputBuilder);
    costFunction.apply(Arrays.asList(inputApplicationResult), environment, inputBuilder);
    FactorGraph inputFactorGraph = inputBuilder.buildNoBranching();

    MaxMarginalSet inputMaxMarginals = marginalCalculator.computeMaxMarginals(inputFactorGraph);
    Assignment costConditionalAssignment = inputMaxMarginals.getNthBestAssignment(0);
    double costConditionalScore = inputFactorGraph.getUnnormalizedLogProbability(
        costConditionalAssignment);
    
    // Evaluate the nondeterministic function on the input value 
    // then condition the distribution on the true output. 
    ParametricBfgBuilder outputBuilder = new ParametricBfgBuilder(true);
    inputApplicationResult = instantiatedModel.apply(input, environment, outputBuilder);
    outputFunction.apply(Arrays.asList(inputApplicationResult), environment, outputBuilder);
    FactorGraph outputFactorGraph = outputBuilder.buildNoBranching();

    MaxMarginalSet outputMaxMarginals = marginalCalculator.computeMaxMarginals(outputFactorGraph);
    Assignment outputConditionalAssignment = outputMaxMarginals.getNthBestAssignment(0);
    double outputConditionalScore = outputFactorGraph.getUnnormalizedLogProbability(
        outputConditionalAssignment);

    // Compute the gradient as a function of the two assignments.
    incrementSufficientStatistics(inputBuilder, parameterSpec, gradient, currentParameters,
        costConditionalAssignment, -1.0);
    incrementSufficientStatistics(outputBuilder, parameterSpec, gradient, currentParameters,
        outputConditionalAssignment, 1.0);

    return Math.min(0.0, outputConditionalScore - costConditionalScore);
  }

  private static void incrementSufficientStatistics(ParametricBfgBuilder builder,
      ParameterSpec spec, SufficientStatistics gradient, SufficientStatistics currentParameters,
      Assignment assignment, double multiplier) {
    for (MarkedVars mark : builder.getMarkedVars()) {
      VariableNumMap vars = mark.getVars();
      ParametricFactor pf = mark.getFactor();
      SufficientStatistics factorGradient = spec.getParametersByIds(
          mark.getParameterIds(), gradient);
      SufficientStatistics factorCurrentParameters = spec.getParametersByIds(
          mark.getParameterIds(), currentParameters);
      VariableRelabeling relabeling = mark.getVarsToFactorRelabeling();

      // Figure out which variables have been conditioned on.
      Assignment factorAssignment = assignment.intersection(vars.getVariableNumsArray());
      Assignment relabeledAssignment = factorAssignment.mapVariables(
          relabeling.getVariableIndexReplacementMap());

      pf.incrementSufficientStatisticsFromAssignment(factorGradient, factorCurrentParameters,
          relabeledAssignment, multiplier);
    }
  }
}
