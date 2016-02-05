package com.jayantkrish.jklol.ccg.gi;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.gi.IncrementalEval.IncrementalEvalState;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

public class IncrementalEvalLoglikelihoodOracle implements
  GradientOracle<IncrementalEval, IncrementalEvalExample> {

  private final ParametricIncrementalEval family;
  private final int beamSize;

  public IncrementalEvalLoglikelihoodOracle(ParametricIncrementalEval family, int beamSize) {
    this.family = Preconditions.checkNotNull(family);
    this.beamSize = beamSize;
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }
  
  @Override
  public IncrementalEval instantiateModel(SufficientStatistics parameters) {
    return family.getModelFromParameters(parameters);
  }
  
  @Override
  public double accumulateGradient(SufficientStatistics gradient, SufficientStatistics currentParameters,
      IncrementalEval model, IncrementalEvalExample example, LogFunction log) {

    // Get a distribution over unconditional executions.
    log.startTimer("update_gradient/input_marginal");
    List<IncrementalEvalState> unconditionalStates = model.evaluateBeam(example.getLogicalForm(),
        example.getDiagram(), beamSize);
    
    if (unconditionalStates.size() == 0) {
      throw new ZeroProbabilityError();      
    }
    log.stopTimer("update_gradient/input_marginal");
    
    // Get a distribution on executions conditioned on the label of the example.
    log.startTimer("update_gradient/output_marginal");
    Predicate<IncrementalEvalState> filter = example.getLabelFilter();
    List<IncrementalEvalState> conditionalStates = model.evaluateBeam(example.getLogicalForm(),
        example.getDiagram(), filter, beamSize);
    
    if (conditionalStates.size() == 0) {
      throw new ZeroProbabilityError();
    }
    log.stopTimer("update_gradient/output_marginal");
    
    double unconditionalPartitionFunction = getPartitionFunction(unconditionalStates);
    for (IncrementalEvalState state : unconditionalStates) {
      family.incrementSufficientStatistics(gradient, currentParameters, example.getLogicalForm(),
          state, -1.0 * state.getProb() / unconditionalPartitionFunction);
    }
    
    double conditionalPartitionFunction = getPartitionFunction(conditionalStates);
    for (IncrementalEvalState state : conditionalStates) {
      family.incrementSufficientStatistics(gradient, currentParameters, example.getLogicalForm(),
          state, state.getProb() / conditionalPartitionFunction);
    }
    
    // Note that the returned loglikelihood is an approximation because
    // inference is approximate.
    return Math.log(conditionalPartitionFunction) - Math.log(unconditionalPartitionFunction);
  }
  
  public double getPartitionFunction(Collection<IncrementalEvalState> states) {
    double partitionFunction = 0.0;
    for (IncrementalEvalState state : states) {
      partitionFunction += state.getProb();
    }
    return partitionFunction;
  }
}
