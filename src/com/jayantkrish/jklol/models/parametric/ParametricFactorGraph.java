package com.jayantkrish.jklol.models.parametric;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.FactorMarginalSet;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A parametric family of graphical models. This class can represent either a
 * loglinear model or Bayesian Network, depending on the types of factors it is
 * constructed with. See {@link BayesNetBuilder} and
 * {@link ParametricFactorGraphBuilder} for how to construct each type of model.
 * 
 * This class simply delegates all of its methods to the corresponding methods
 * of {@link ParametricFactor}, then aggregates and returns the results.
 * 
 * @author jayantk
 */
public class ParametricFactorGraph extends AbstractParametricFamily<SufficientStatistics> {

  private List<ParametricFactor<SufficientStatistics>> parametricFactors;
  private List<VariablePattern> factorPatterns;

  public ParametricFactorGraph(DynamicFactorGraph factorGraph, 
      List<ParametricFactor<SufficientStatistics>> parametricFactors, 
      List<VariablePattern> factorPatterns) {
    super(factorGraph);
    Preconditions.checkArgument(parametricFactors.size() == factorPatterns.size());
    this.parametricFactors = Lists.newArrayList(parametricFactors);
    this.factorPatterns = Lists.newArrayList(factorPatterns);
  }

  /**
   * Gets the factors in this model which are parameterized.
   * 
   * @return
   */
  public List<ParametricFactor<SufficientStatistics>> getParametricFactors() {
    return Collections.unmodifiableList(parametricFactors);
  }

  @Override
  public DynamicFactorGraph getFactorGraphFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    Preconditions.checkArgument(parameterList.size() == parametricFactors.size());
    List<PlateFactor> plateFactors = Lists.newArrayList();
    for (int i = 0; i < parameterList.size(); i++) {
      plateFactors.add(new PlateFactor(
          parametricFactors.get(i).getFactorFromParameters(parameterList.get(i)),
          factorPatterns.get(i)));
    }
    return getBaseFactorGraph().addPlateFactors(plateFactors);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> sufficientStatistics = Lists.newArrayList();
    for (ParametricFactor<SufficientStatistics> factor : getParametricFactors()) {
      sufficientStatistics.add(factor.getNewSufficientStatistics());
    }
    return new ListSufficientStatistics(sufficientStatistics);
  }

  @Override
  public void incrementSufficientStatistics(SufficientStatistics statistics,  
      VariableNumMap variables, Assignment assignment, double count) {
    incrementSufficientStatistics(statistics, FactorMarginalSet.fromAssignment(
        variables, assignment), count);
  }

  @Override
  public void incrementSufficientStatistics(SufficientStatistics statistics, 
      MarginalSet marginals, double count) {
    List<SufficientStatistics> statisticsList = statistics.coerceToList().getStatistics();
    Preconditions.checkArgument(statisticsList.size() == parametricFactors.size());
    
    for (int i = 0; i < statisticsList.size(); i++) {
      VariablePattern pattern = factorPatterns.get(i);
      List<VariableMatch> matches = pattern.matchVariables(marginals.getVariables());

      for (VariableMatch match : matches) {
        VariableNumMap fixedVars = match.getMatchedVariables().intersection(marginals.getConditionedValues().getVariableNums());
        VariableNumMap marginalVars = match.getMatchedVariables().removeAll(marginals.getConditionedValues().getVariableNums());
       
        Factor factorMarginal = marginals.getMarginal(marginalVars.getVariableNums());
        Assignment factorAssignment = marginals.getConditionedValues().intersection(fixedVars);

        parametricFactors.get(i).incrementSufficientStatisticsFromMarginal(statisticsList.get(i), 
            factorMarginal.relabelVariables(match.getMappingToTemplate()), 
            factorAssignment.mapVariables(match.getMappingToTemplate().getVariableIndexReplacementMap()),
            count, marginals.getPartitionFunction());
      }
    }
  }
  
  /**
   * Gets some basic statistics about {@code this}.  
   * @return
   */
  public String summary() {
    StringBuilder sb = new StringBuilder();
    sb.append("ParametricFactorGraph: ");
    sb.append(getBaseFactorGraph().getVariables() + " variables, ");
    sb.append(parametricFactors.size() + " parameteric factors, ");
    return sb.toString();
  }
}
