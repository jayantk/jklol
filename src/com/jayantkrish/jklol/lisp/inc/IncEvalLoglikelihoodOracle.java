package com.jayantkrish.jklol.lisp.inc;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class IncEvalLoglikelihoodOracle implements
  GradientOracle<IncEval, IncEvalExample> {

  private final ParametricIncEval family;
  private final int beamSize;

  public IncEvalLoglikelihoodOracle(ParametricIncEval family, int beamSize) {
    this.family = Preconditions.checkNotNull(family);
    this.beamSize = beamSize;
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }
  
  @Override
  public IncEval instantiateModel(SufficientStatistics parameters) {
    return family.getModelFromParameters(parameters);
  }
  
  @Override
  public double accumulateGradient(SufficientStatistics gradient, SufficientStatistics currentParameters,
      IncEval model, IncEvalExample example, LogFunction log) {

    // Get a distribution over unconditional executions.
    log.startTimer("update_gradient/input_marginal");
    List<IncEvalState> unconditionalStates = model.evaluateBeam(example.getLogicalForm(),
        example.getDiagram(), null, model.getEnvironment(), log, beamSize);
    
    if (unconditionalStates.size() == 0) {
      System.out.println("unconditional search failure");
      throw new ZeroProbabilityError();      
    }
    log.stopTimer("update_gradient/input_marginal");
    
    // Get a distribution on executions conditioned on the label of the example.
    log.startTimer("update_gradient/output_marginal");
    Predicate<IncEvalState> filter = example.getLabelFilter();
    List<IncEvalState> conditionalStates = model.evaluateBeam(example.getLogicalForm(),
        example.getDiagram(), filter, model.getEnvironment(), log, beamSize);
    
    if (conditionalStates.size() == 0) {
      System.out.println("conditional search failure");
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/output_marginal");

    log.startTimer("update_gradient/increment_gradient");
    double unconditionalPartitionFunction = getPartitionFunction(unconditionalStates);
    for (IncEvalState state : unconditionalStates) {
      family.incrementSufficientStatistics(gradient, currentParameters, example.getLogicalForm(),
          state, -1.0 * state.getProb() / unconditionalPartitionFunction);
    }

    double conditionalPartitionFunction = getPartitionFunction(conditionalStates);
    for (IncEvalState state : conditionalStates) {
      family.incrementSufficientStatistics(gradient, currentParameters, example.getLogicalForm(),
          state, state.getProb() / conditionalPartitionFunction);
    }
    log.stopTimer("update_gradient/increment_gradient");

    // Note that the returned loglikelihood is an approximation because
    // inference is approximate.
    return Math.log(conditionalPartitionFunction) - Math.log(unconditionalPartitionFunction);
  }
  
  public double getPartitionFunction(Collection<IncEvalState> states) {
    double partitionFunction = 0.0;
    for (IncEvalState state : states) {
      partitionFunction += state.getProb();
    }
    return partitionFunction;
  }
}
