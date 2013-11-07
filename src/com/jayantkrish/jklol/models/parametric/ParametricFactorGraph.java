package com.jayantkrish.jklol.models.parametric;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.FactorMarginalSet;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.LogFunctions;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A parametric family of graphical models. This class can represent
 * either a loglinear model or Bayesian Network, depending on the
 * types of factors it is constructed with. See
 * {@link BayesNetBuilder} and {@link ParametricFactorGraphBuilder}
 * for how to construct each type of model.
 * <p>
 * This class simply delegates all of its methods to the corresponding
 * methods of {@link ParametricFactor}, then aggregates and returns
 * the results.
 * 
 * @author jayantk
 */
public class ParametricFactorGraph implements ParametricFamily<DynamicFactorGraph> {

  private static final long serialVersionUID = 1L;

  private final DynamicFactorGraph baseFactorGraph;

  private final List<ParametricFactor> parametricFactors;
  private final List<VariablePattern> factorPatterns;
  private final IndexedList<String> factorNames;

  public ParametricFactorGraph(DynamicFactorGraph factorGraph,
      List<ParametricFactor> parametricFactors, List<VariablePattern> factorPatterns,
      List<String> factorNames) {
    Preconditions.checkArgument(parametricFactors.size() == factorPatterns.size());
    Preconditions.checkArgument(parametricFactors.size() == factorNames.size());
    this.baseFactorGraph = factorGraph;
    this.parametricFactors = ImmutableList.copyOf(parametricFactors);
    this.factorPatterns = ImmutableList.copyOf(factorPatterns);
    this.factorNames = IndexedList.create(factorNames);
  }

  /**
   * Gets the variables over which the distributions in this family
   * are defined. All {@code DynamicFactorGraph}s returned by
   * {@link #getModelFromParameters(Object)} are defined over the same
   * variables.
   * 
   * @return
   */
  public DynamicVariableSet getVariables() {
    return baseFactorGraph.getVariables();
  }

  /**
   * Gets the factors in this model which are parameterized.
   * 
   * @return
   */
  public List<ParametricFactor> getParametricFactors() {
    return Collections.unmodifiableList(parametricFactors);
  }

  public ParametricFactor getParametricFactorByName(String name) {
    if (!factorNames.contains(name)) {
      return null;
    }
    int index = factorNames.getIndex(name);
    return parametricFactors.get(index);
  }

  /**
   * Gets a {@code DynamicFactorGraph} which is the member of this
   * family indexed by {@code parameters}. Note that multiple values
   * of {@code parameters} may result in the same {@code FactorGraph}.
   * 
   * @param parameters
   * @return
   */
  public DynamicFactorGraph getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    Preconditions.checkArgument(parameterList.size() == parametricFactors.size());
    List<PlateFactor> plateFactors = Lists.newArrayList();
    for (int i = 0; i < parameterList.size(); i++) {
      plateFactors.add(new ReplicatedFactor(
          parametricFactors.get(i).getModelFromParameters(parameterList.get(i)),
          factorPatterns.get(i)));
    }
    return baseFactorGraph.addPlateFactors(plateFactors, factorNames.items());
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    StringBuilder builder = new StringBuilder();
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    Preconditions.checkArgument(parameterList.size() == parametricFactors.size());
    for (int i = 0; i < parameterList.size(); i++) {
      builder.append(parametricFactors.get(i).getParameterDescription(parameterList.get(i), numFeatures));
    }
    return builder.toString();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The returned statistics have names corresponding to the
   * parametric factors in this. That is, the parameters for a
   * particular parametric factor in this factor graph can be
   * retrieved using
   * {@link ListSufficientStatistics#getStatisticByName(String)}.
   * 
   * @return
   */
  @Override
  public ListSufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> sufficientStatistics = Lists.newArrayList();
    for (ParametricFactor factor : getParametricFactors()) {
      sufficientStatistics.add(factor.getNewSufficientStatistics());
    }
    return new ListSufficientStatistics(factorNames.items(), sufficientStatistics);
  }

  /**
   * Accumulates sufficient statistics (in {@code statistics}) for
   * estimating a model from {@code this} family based on a point
   * distribution at {@code assignment}. {@code count} is the number
   * of times that {@code assignment} has been observed in the
   * training data, and acts as a multiplier for the computed
   * statistics {@code assignment} must contain a value for all of the
   * variables in {@code this.getVariables()}.
   * 
   * @param statistics
   * @param assignment
   * @param count
   */
  public void incrementSufficientStatistics(SufficientStatistics statistics,
      VariableNumMap variables, Assignment assignment, double count) {
    incrementSufficientStatistics(statistics, FactorMarginalSet.fromAssignment(
        variables, assignment, 1.0), count);
  }

  /**
   * Computes a vector of sufficient statistics for {@code this} and
   * accumulates them in {@code statistics}. The statistics are
   * computed from the (conditional) marginal distribution
   * {@code marginals}. {@code count} is the number of times
   * {@code marginals} has been observed in the training data.
   * 
   * @param statistics
   * @param marginals
   * @param count
   */
  public void incrementSufficientStatistics(SufficientStatistics statistics,
      MarginalSet marginals, double count) {
    LogFunction log = LogFunctions.getLogFunction();
    log.startTimer("parametric_factor_graph_increment");
    List<SufficientStatistics> statisticsList = statistics.coerceToList().getStatistics();
    Preconditions.checkArgument(statisticsList.size() == parametricFactors.size());
    
    int[] conditionedVariableNums = marginals.getConditionedValues().getVariableNumsArray();
    for (int i = 0; i < statisticsList.size(); i++) {
      // log.startTimer("parametric_factor_graph_match");
      VariablePattern pattern = factorPatterns.get(i);
      List<VariableMatch> matches = pattern.matchVariables(marginals.getVariables());
      // log.stopTimer("parametric_factor_graph_match");
      for (VariableMatch match : matches) {
        // log.startTimer("parametric_factor_graph_factor_stuff");
        VariableNumMap matchVars = match.getMatchedVariables();
        // These calls take ~ 4 microseconds
        VariableNumMap fixedVars = matchVars.intersection(conditionedVariableNums);
        VariableNumMap marginalVars = matchVars.removeAll(conditionedVariableNums);

        // to here: 6 microsecs
        log.startTimer("parametric_factor_graph_marginal");
        Factor factorMarginal = marginals.getMarginal(marginalVars.getVariableNumsArray());
        Assignment factorAssignment = marginals.getConditionedValues().intersection(fixedVars);
        log.stopTimer("parametric_factor_graph_marginal");

        // to here: 13 microsecs
        Factor relabeledMarginal = factorMarginal.relabelVariables(match.getMappingToTemplate());
        Assignment relabeledAssignment = factorAssignment.mapVariables(match.getMappingToTemplate()
            .getVariableIndexReplacementMap());
        // log.stopTimer("parametric_factor_graph_factor_stuff");
        // to here: 18 microsecs
        
        log.startTimer("parametric_factor_graph_increment/increment");
        parametricFactors.get(i).incrementSufficientStatisticsFromMarginal(statisticsList.get(i),
            relabeledMarginal, relabeledAssignment, count, 1.0);
        // to here: 27 microsecs
        log.stopTimer("parametric_factor_graph_increment/increment");
      }
    }
    log.stopTimer("parametric_factor_graph_increment");
  }

  /**
   * Gets some basic statistics about {@code this}.
   * 
   * @return
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ParametricFactorGraph: ");
    sb.append(baseFactorGraph.getVariables() + " variables, ");
    sb.append(parametricFactors.size() + " parametric factors, ");
    for (ParametricFactor factor : parametricFactors) {
      sb.append(factor.toString());
    }
    sb.append("base factor graph: " + baseFactorGraph);
    return sb.toString();
  }
}
