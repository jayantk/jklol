package com.jayantkrish.jklol.training;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Gradient oracle that trains a parametric factor to
 * have the same (conditional) distribution as a given
 * factor.
 * 
 * @author jayantk
 */
public class FactorLoglikelihoodOracle implements GradientOracle<Factor, Void> {
  
  private final ParametricFactor family;
  private final Factor target;
  private final VariableNumMap conditionalVars;
  
  public FactorLoglikelihoodOracle(ParametricFactor family, Factor target,
      VariableNumMap conditionalVars) {
    this.family = Preconditions.checkNotNull(family);
    this.target = Preconditions.checkNotNull(target);
    this.conditionalVars = Preconditions.checkNotNull(conditionalVars);
  }
  
  @Override
  public SufficientStatistics initializeGradient() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public Factor instantiateModel(SufficientStatistics parameters) {
    return family.getModelFromParameters(parameters);
  }

  @Override
  public double accumulateGradient(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Factor instantiatedModel, Void example,
      LogFunction log) {
    double logProb = 0.0;
    Iterator<Assignment> iter = new AllAssignmentIterator(conditionalVars);
    while (iter.hasNext()) {
      Assignment a = iter.next();
      
      log.startTimer("gradient/condition");
      DiscreteFactor predictedConditional = instantiatedModel.conditional(a).coerceToDiscrete();
      double predictedPartitionFunction = predictedConditional.getTotalUnnormalizedProbability();

      DiscreteFactor targetConditional = target.conditional(a).coerceToDiscrete();
      // Target partition function is also the total number of examples
      // observed for the conditioned-on assignment.
      double targetPartitionFunction = targetConditional.getTotalUnnormalizedProbability();
      double count = targetPartitionFunction;
      log.stopTimer("gradient/condition");

      log.startTimer("gradient/increment");
      family.incrementSufficientStatisticsFromMarginal(gradient, currentParameters,
          predictedConditional, a, -1.0 * count, predictedPartitionFunction);
      family.incrementSufficientStatisticsFromMarginal(gradient, currentParameters,
          targetConditional, a, count, targetPartitionFunction);
      log.stopTimer("gradient/increment");

      Tensor logProbs = predictedConditional.getWeights()
          .elementwiseProduct(1.0 / predictedPartitionFunction).elementwiseLog();

      logProb += logProbs.innerProduct(targetConditional.getWeights()).getByDimKey();
    }
    return logProb;
  }
}
