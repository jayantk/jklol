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

public class AmbLispGradientOracle implements GradientOracle<AmbFunctionValue, Example<List<Object>, AmbFunctionValue>> {

  private final AmbFunctionValue family;
  private final Environment environment;
  private final ParameterSpec parameterSpec;

  private final MarginalCalculator marginalCalculator;

  public AmbLispGradientOracle(AmbFunctionValue family, Environment environment,
      ParameterSpec parameterSpec, MarginalCalculator marginalCalculator) {
    this.family = Preconditions.checkNotNull(family);
    this.environment = Preconditions.checkNotNull(environment);
    this.parameterSpec = Preconditions.checkNotNull(parameterSpec);
    this.marginalCalculator = Preconditions.checkNotNull(marginalCalculator);
  }

  @Override
  public AmbFunctionValue instantiateModel(SufficientStatistics parameters) {
    ParametricBfgBuilder newBuilder = new ParametricBfgBuilder(true);
    Object value = family.apply(Arrays.<Object>asList(parameters), environment, newBuilder);
    Preconditions.checkState(value instanceof AmbFunctionValue);    
    return (AmbFunctionValue) value;
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return parameterSpec.getParameters();
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient,
      AmbFunctionValue instantiatedModel, Example<List<Object>, AmbFunctionValue> example, LogFunction log) {
    // Evaluate the nondeterministic function on the current example to 
    // produce a factor graph for the distribution over outputs.
    ParametricBfgBuilder newBuilder = new ParametricBfgBuilder(true);
    Object inputApplicationResult = instantiatedModel.apply(example.getInput(), environment, newBuilder);
    ParametricFactorGraph pfg = newBuilder.buildNoBranching();

    System.out.println("parametric factors: " + pfg.getParametricFactors());
    FactorGraph inputFactorGraph = pfg.getModelFromParameters(pfg.getNewSufficientStatistics()).conditional(DynamicAssignment.EMPTY);

    // Compute the marginal distribution in this factor graph for
    // the first gradient term.
    MarginalSet inputMarginals = marginalCalculator.computeMarginals(inputFactorGraph);
    
    // Apply the output filter.
    AmbFunctionValue outputCondition = example.getOutput();
    outputCondition.apply(Arrays.asList(inputApplicationResult), environment, newBuilder);
    pfg = newBuilder.buildNoBranching();
    FactorGraph outputFactorGraph = pfg.getModelFromParameters(pfg.getNewSufficientStatistics()).conditional(DynamicAssignment.EMPTY);

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
    incrementSufficientStatistics(newBuilder, gradient, inputMarginals, -1.0);
    // System.out.println("=== input marginals ===");
    // System.out.println(inputMarginals);
    // System.out.println(family.getParameterDescription(gradient));

    incrementSufficientStatistics(newBuilder, gradient, outputMarginals, 1.0);
    // System.out.println("=== output marginals ===");
    // System.out.println(outputMarginals);
    // System.out.println(gradient);
    log.stopTimer("update_gradient/increment");

    return outputLogPartitionFunction - inputLogPartitionFunction;
  }
  
  private static void incrementSufficientStatistics(ParametricBfgBuilder builder,
      SufficientStatistics parameters, MarginalSet marginals, double multiplier) {
    for (MarkedVars mark : builder.getMarkedVars()) {
      VariableNumMap vars = mark.getVars();
      ParametricFactor pf = mark.getFactor();
      Factor marginal = marginals.getMarginal(vars).relabelVariables(VariableRelabeling.createFromVariables(vars, pf.getVars()));

      pf.incrementSufficientStatisticsFromMarginal(parameters, marginal,
          Assignment.EMPTY, multiplier, 1.0);
    }
  }
}
