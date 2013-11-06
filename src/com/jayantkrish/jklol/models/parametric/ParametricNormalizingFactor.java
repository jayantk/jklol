package com.jayantkrish.jklol.models.parametric;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Factors;
import com.jayantkrish.jklol.models.NormalizingFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Locally normalizes the probability distribution defined by a wrapped
 * {@code ParametricFactor}. This factor is used to define graphical models
 * with conditional probability distributions.
 * 
 * @author jayantk
 */
public class ParametricNormalizingFactor extends AbstractParametricFactor {

  private static final long serialVersionUID = -7699484615570829044L;
  
  private final List<ParametricFactor> wrappedFactors;
  
  private final VariableNumMap inputVars;
  private final VariableNumMap conditionalVars;
  private final VariableNumMap outputVars;
  
  public ParametricNormalizingFactor(VariableNumMap inputVars, VariableNumMap conditionalVars,
      VariableNumMap outputVars, List<ParametricFactor> wrappedFactors) {
    super(VariableNumMap.unionAll(inputVars, conditionalVars, outputVars));
    this.inputVars = Preconditions.checkNotNull(inputVars);
    this.conditionalVars = Preconditions.checkNotNull(conditionalVars);
    this.outputVars = Preconditions.checkNotNull(outputVars);
    this.wrappedFactors = Preconditions.checkNotNull(wrappedFactors);
    
    VariableNumMap wrappedVars = VariableNumMap.emptyMap();
    for (ParametricFactor factor : wrappedFactors) {
      wrappedVars = wrappedVars.union(factor.getVars());
    }
    Preconditions.checkArgument(wrappedVars.equals(getVars()));
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics parameters) {
    List<Factor> factors = Lists.newArrayList();
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    for (int i = 0; i < wrappedFactors.size(); i++) {
      factors.add(wrappedFactors.get(i).getModelFromParameters(parameterList.get(i)));
    }
    
    if (inputVars.size() == 0) {
      // Normalize the returned factor
      Factor factor = Factors.product(factors);
      return factor.product(factor.marginalize(outputVars).inverse());
    } else {
      return new NormalizingFactor(inputVars, conditionalVars, outputVars, factors);
    }
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    StringBuilder sb = new StringBuilder();
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    for (int i = 0; i < wrappedFactors.size(); i++) {
      sb.append(wrappedFactors.get(i).getParameterDescription(parameterList.get(i), numFeatures));
    }
    return sb.toString();
  }

  @Override
  public String getParameterDescriptionXML(SufficientStatistics parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> parameterList = Lists.newArrayList();
    List<String> names = Lists.newArrayList();
    for (int i = 0; i < wrappedFactors.size(); i++) {
      names.add(Integer.toString(i));
      parameterList.add(wrappedFactors.get(i).getNewSufficientStatistics());
    }
    return new ListSufficientStatistics(names, parameterList);
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics parameters, 
      Assignment assignment, double count) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    for (int i = 0; i < wrappedFactors.size(); i++) {
      wrappedFactors.get(i).incrementSufficientStatisticsFromAssignment(parameterList.get(i),
          assignment, count);
    }
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics parameters, 
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    VariableNumMap marginalVars = marginal.getVars();
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    for (int i = 0; i < wrappedFactors.size(); i++) {
      ParametricFactor wrappedFactor = wrappedFactors.get(i);
      VariableNumMap varsNotInFactor = marginalVars.removeAll(wrappedFactor.getVars());
      Factor curMarginal = marginal.marginalize(varsNotInFactor);

      wrappedFactor.incrementSufficientStatisticsFromMarginal(parameterList.get(i), curMarginal, 
        conditionalAssignment, count, partitionFunction);
    }
  }
}
