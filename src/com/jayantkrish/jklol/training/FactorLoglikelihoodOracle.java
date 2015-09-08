package com.jayantkrish.jklol.training;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
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
  
  private final int[] dimsToSum;
  private final Tensor targetConditional;
  private final Tensor targetCounts;
  private final double totalCount;
  
  public FactorLoglikelihoodOracle(ParametricFactor family, Factor target,
      VariableNumMap conditionalVars) {
    this.family = Preconditions.checkNotNull(family);
    this.target = Preconditions.checkNotNull(target);
    this.conditionalVars = Preconditions.checkNotNull(conditionalVars);

    this.dimsToSum = target.getVars().removeAll(conditionalVars).getVariableNumsArray();
    this.targetConditional = target.coerceToDiscrete().getWeights();
    this.targetCounts = targetConditional.sumOutDimensions(dimsToSum);
    this.totalCount = target.getTotalUnnormalizedProbability();
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
    log.startTimer("factor_oracle/predicted_conditional");
    Tensor predictedWeights = instantiatedModel.coerceToDiscrete().getWeights();
    Tensor denominators = predictedWeights.sumOutDimensions(dimsToSum);
    Tensor predictedConditional = predictedWeights.elementwiseProduct(denominators.elementwiseInverse());
    log.stopTimer("factor_oracle/predicted_conditional");
    
    log.startTimer("factor_oracle/product");
    Factor predictedConditionalFactor = new TableFactor(instantiatedModel.getVars(),
        predictedConditional.elementwiseProduct(targetCounts));
    log.stopTimer("factor_oracle/product");
    
    log.startTimer("factor_oracle/increment");
    family.incrementSufficientStatisticsFromMarginal(gradient, currentParameters, predictedConditionalFactor,
        Assignment.EMPTY, -1.0 / totalCount, 1.0);
    family.incrementSufficientStatisticsFromMarginal(gradient, currentParameters, target,
        Assignment.EMPTY, 1.0 / totalCount, 1.0);
    log.stopTimer("factor_oracle/increment");
    
    log.startTimer("factor_oracle/objective");
    double objective = predictedConditional.elementwiseLogSparse().innerProduct(targetConditional).getByDimKey() / totalCount;
    log.stopTimer("factor_oracle/objective");
    
    return objective;
  }
}
