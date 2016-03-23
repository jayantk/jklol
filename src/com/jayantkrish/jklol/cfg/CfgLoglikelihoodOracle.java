package com.jayantkrish.jklol.cfg;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Gradient oracle implementing the loglikelihood objective
 * function for training a CFG parser from sentences with
 * annotated parse trees.
 * 
 * @author jayant
 *
 */
public class CfgLoglikelihoodOracle implements GradientOracle<CfgParser, CfgExample> {
  
  private final ParametricCfgParser family;
  
  public CfgLoglikelihoodOracle(ParametricCfgParser family) {
    this.family = Preconditions.checkNotNull(family);
  }

  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public CfgParser instantiateModel(SufficientStatistics parameters) {
    return family.getModelFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient,
      SufficientStatistics currentParameters, CfgParser instantiatedModel, CfgExample example,
      LogFunction log) {
    
    log.startTimer("update_gradient/input_marginal");
    CfgParseChart chart = instantiatedModel.parseMarginal(example.getWords(), true);
    log.stopTimer("update_gradient/input_marginal");
    
    log.startTimer("update_gradient/increment_gradient");
    double unconditionalPartitionFunction = chart.getPartitionFunction();
    family.incrementSufficientStatisticsFromParseChart(gradient, currentParameters, chart,
        -1.0, unconditionalPartitionFunction);
    
    family.incrementSufficientStatisticsFromParseTree(gradient, currentParameters,
        example.getParseTree(), 1.0);
    log.stopTimer("update_gradient/increment_gradient");
    
    double inputLogPartitionFunction = Math.log(unconditionalPartitionFunction);
    double outputLogPartitionFunction = Math.log(instantiatedModel.getProbability(example.getParseTree()));
    
    return outputLogPartitionFunction - inputLogPartitionFunction;
  }
}
