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
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
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
public class AmbLispLoglikelihoodOracle implements GradientOracle<AmbFunctionValue, Example<List<Object>, Object>> {

  private final AmbFunctionValue family;
  private final EvalContext context;
  private final ParameterSpec parameterSpec;

  private final MarginalCalculator marginalCalculator;

  public AmbLispLoglikelihoodOracle(AmbFunctionValue family, EvalContext context,
      ParameterSpec parameterSpec, MarginalCalculator marginalCalculator) {
    this.family = Preconditions.checkNotNull(family);
    this.context = Preconditions.checkNotNull(context);
    this.parameterSpec = Preconditions.checkNotNull(parameterSpec);
    this.marginalCalculator = Preconditions.checkNotNull(marginalCalculator);
  }
  
  @Override
  public AmbFunctionValue instantiateModel(SufficientStatistics parameters) {
    ParametricBfgBuilder newBuilder = new ParametricBfgBuilder(true);
    Object value = family.apply(Arrays.<Object>asList(new SpecAndParameters(parameterSpec, parameters)),
        context, newBuilder);
    Preconditions.checkState(value instanceof AmbFunctionValue);    
    return (AmbFunctionValue) value;
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return parameterSpec.getNewParameters();
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient, SufficientStatistics currentParameters,
      AmbFunctionValue instantiatedModel, Example<List<Object>, Object> example, LogFunction log) {
    // Evaluate the nondeterministic function on the current example to 
    // produce a factor graph for the distribution over outputs.
    log.startTimer("compute_gradient/input_eval");
    log.startTimer("compute_gradient/input_eval/builder");
    ParametricBfgBuilder newBuilder = new ParametricBfgBuilder(true);
    log.stopTimer("compute_gradient/input_eval/builder");

    log.startTimer("compute_gradient/input_eval/eval");
    Object inputApplicationResult = instantiatedModel.apply(example.getInput(), context, newBuilder);
    log.stopTimer("compute_gradient/input_eval/eval");

    log.startTimer("compute_gradient/input_eval/fg");
    FactorGraph inputFactorGraph = newBuilder.buildNoBranching();
    Assignment inputAssignment = newBuilder.getAssignment();
    log.stopTimer("compute_gradient/input_eval/fg");
    log.stopTimer("compute_gradient/input_eval");

    // Compute the marginal distribution in this factor graph for
    // the first gradient term.
    log.startTimer("compute_gradient/input_marginals");
    MarginalSet inputMarginals = marginalCalculator.computeMarginals(inputFactorGraph);
    log.stopTimer("compute_gradient/input_marginals");

    // Apply the output filter.
    Object outputValue = example.getOutput();
    MarginalSet outputMarginals = null;
    Assignment outputAssignment = null;
    if (outputValue instanceof AmbFunctionValue) {
      AmbFunctionValue outputCondition = (AmbFunctionValue) outputValue;
      log.startTimer("compute_gradient/output_eval");
      log.startTimer("compute_gradient/output_eval/eval");

      outputCondition.apply(Arrays.asList(inputApplicationResult), context, newBuilder);
      log.stopTimer("compute_gradient/output_eval/eval");

      log.startTimer("compute_gradient/output_eval/fg");
      FactorGraph outputFactorGraph = newBuilder.buildNoBranching();
      outputAssignment = newBuilder.getAssignment();
      log.stopTimer("compute_gradient/output_eval/fg");
      log.stopTimer("compute_gradient/output_eval");
      
      // Compute the marginal distribution given the constraint on the
      // output.
      log.startTimer("compute_gradient/output_marginals");
      outputMarginals = marginalCalculator.computeMarginals(outputFactorGraph);
      log.stopTimer("compute_gradient/output_marginals");
    } else {
      log.startTimer("compute_gradient/output_fixed_value");
      if (inputApplicationResult instanceof AmbValue) {
        Assignment outputConditionAssignment = ((AmbValue) inputApplicationResult).getVar()
            .outcomeArrayToAssignment(outputValue);
        FactorGraph outputFactorGraph = inputFactorGraph.conditional(outputConditionAssignment);
        outputAssignment = inputAssignment.union(outputConditionAssignment);
        outputMarginals = marginalCalculator.computeMarginals(outputFactorGraph);
      } else {
        if (inputApplicationResult == outputValue) {
          outputMarginals = inputMarginals;
          outputAssignment = inputAssignment;
        } else {
          // Evaluating the model cannot produce the labeled value.
          throw new ZeroProbabilityError();
        }
      }
      log.stopTimer("compute_gradient/output_fixed_value");
    }

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
    log.startTimer("compute_gradient/increment_parameters");
    incrementSufficientStatistics(newBuilder, parameterSpec, gradient, currentParameters,
        inputMarginals, inputAssignment, -1.0);
    // System.out.println("=== input marginals ===");
    // System.out.println(inputMarginals);
    // System.out.println(gradient);
    // System.out.println("gradient l2: " + gradient.getL2Norm());

    incrementSufficientStatistics(newBuilder, parameterSpec, gradient, currentParameters,
        outputMarginals, outputAssignment, 1.0);
    // System.out.println("=== output marginals ===");
    // System.out.println(outputMarginals);
    // System.out.println(gradient);
    // System.out.println("gradient l2: " + gradient.getL2Norm());
    log.stopTimer("compute_gradient/increment_parameters");

    return outputLogPartitionFunction - inputLogPartitionFunction;
  }

  private static void incrementSufficientStatistics(ParametricBfgBuilder builder,
      ParameterSpec spec, SufficientStatistics gradient, SufficientStatistics currentParameters,
      MarginalSet marginals, Assignment assignment, double multiplier) {
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
