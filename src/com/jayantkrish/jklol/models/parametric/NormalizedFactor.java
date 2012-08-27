package com.jayantkrish.jklol.models.parametric;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Locally normalizes the probability distribution defined by a wrapped
 * {@code ParametricFactor}. This factor is used to define graphical models
 * with conditional probability distributions.
 * 
 * @author jayantk
 */
public class NormalizedFactor extends AbstractParametricFactor {

  private static final long serialVersionUID = -7699484615570829044L;
  
  private final ParametricFactor wrappedFactor;
  // This factor defines a conditional distribution over these variables,
  // given the values of all other variables.
  private final VariableNumMap conditionalVariables;
  
  public NormalizedFactor(ParametricFactor wrappedFactor, VariableNumMap conditionalVariables) {
    super(wrappedFactor.getVars());
    Preconditions.checkArgument(wrappedFactor.getVars().containsAll(conditionalVariables));
    this.wrappedFactor = wrappedFactor;
    this.conditionalVariables = conditionalVariables;
  }

  @Override
  public Factor getFactorFromParameters(SufficientStatistics parameters) {
    Factor factor = wrappedFactor.getFactorFromParameters(parameters);

    if (conditionalVariables.size() > 0) {
      // Normalize the returned factor.
      Factor normalization = factor.marginalize(conditionalVariables).inverse();
      return factor.product(normalization);
    } else {
      return factor;
    }
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return wrappedFactor.getParameterDescription(parameters, numFeatures);
  }

  @Override
  public String getParameterDescriptionXML(SufficientStatistics parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return wrappedFactor.getNewSufficientStatistics();
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics, 
      Assignment assignment, double count) {
    wrappedFactor.incrementSufficientStatisticsFromAssignment(statistics, assignment, count);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics, 
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    wrappedFactor.incrementSufficientStatisticsFromMarginal(statistics, marginal, 
        conditionalAssignment, count, partitionFunction);
  }

  @Override
  public ParametricFactorProto toProto(IndexedList<Variable> variableTypeIndex) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
