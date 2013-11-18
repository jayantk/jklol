package com.jayantkrish.jklol.models.parametric;

import java.util.Collections;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A parametric factor which returns a constant factor. This factor is
 * useful for imposing hard constraints when combined with other
 * parametric factors, e.g., using {@link CombiningParametricFactor}.
 * 
 * @author jayantk
 */
public class ConstantParametricFactor extends AbstractParametricFactor {
  private static final long serialVersionUID = 1L;
  
  private final DiscreteFactor factor;

  public ConstantParametricFactor(VariableNumMap variables, DiscreteFactor factor) {
    super(variables);
    Preconditions.checkArgument(factor.getVars().equals(variables));
    this.factor = factor;
  }

  @Override
  public String getParameterDescriptionXML(SufficientStatistics parameters) {
    return "constant\n";
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics parameters) {
    return factor;
  }

  @Override
  public ConstantParametricFactor rescaleFeatures(SufficientStatistics rescaling) {
    return this;
  }
  
  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      Assignment assignment, double count) {}

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics, 
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {}

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return new ListSufficientStatistics(Collections.<String>emptyList(),
        Collections.<SufficientStatistics>emptyList());
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return "constant\n";
  }
}
