package com.jayantkrish.jklol.models.parametric;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.FactorMarginalSet;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.models.dynamic.Plate;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.models.loglinear.LogLinearModelBuilder;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A parametric family of graphical models. This class can represent either a
 * loglinear model or Bayesian Network, depending on the types of factors it is
 * constructed with. See {@link BayesNetBuilder} and
 * {@link LogLinearModelBuilder} for how to construct each type of model.
 * 
 * This class simply delegates all of its methods to the corresponding methods
 * of {@link ParametricFactor}, then aggregates and returns the results.
 * 
 * @author jayantk
 */
public class ParametricFactorGraph extends AbstractParametricFamily<SufficientStatistics> {

  private List<ParametricFactor<SufficientStatistics>> parametricFactors;
  private List<VariablePattern> factorPatterns;

  public ParametricFactorGraph(FactorGraph factorGraph, 
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
  public FactorGraph getFactorGraphFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    Preconditions.checkArgument(parameterList.size() == parametricFactors.size());
    FactorGraph result = getBaseFactorGraph();
    for (int i = 0; i < parameterList.size(); i++) {
      result = result.addPlateFactor(new PlateFactor(
          parametricFactors.get(i).getFactorFromParameters(parameterList.get(i)),
          factorPatterns.get(i), Collections.<Plate>emptyList()));
    }
    return result;
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
    sb.append(getBaseFactorGraph().getVariables().size() + " variables, ");
    sb.append(parametricFactors.size() + " parameteric factors, ");
    sb.append(getBaseFactorGraph().getFactors().size() + " constant factors.\n");

    sb.append("Discrete Variables:\n");
    List<DiscreteVariable> variables = getBaseFactorGraph().getVariables().getDiscreteVariables();
    for (int i = 0; i < variables.size(); i++) {
      sb.append("  " + i + ": " + variables.get(i).toString() + "\n");
    }
    
    sb.append("Constant Factors: \n");
    List<Factor> factors = getBaseFactorGraph().getFactors();
    for (Factor factor : factors) {
      sb.append("  " + factor.getVars() + " size: " + factor.size() + "\n");
    }

    sb.append("Parametric Factors: \n");
    for (ParametricFactor<SufficientStatistics> factor : parametricFactors) {
      sb.append("  " + factor.getVars() + "\n");
    }

    return sb.toString();
  }
}
