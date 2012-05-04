package com.jayantkrish.jklol.models.parametric;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.FactorMarginalSet;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableProtos.VariablePatternProto;
import com.jayantkrish.jklol.models.VariableProtos.VariableProto;
import com.jayantkrish.jklol.models.Variables;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.models.dynamic.VariablePatterns;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorGraphProto;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

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
public class ParametricFactorGraph {

  private final DynamicFactorGraph baseFactorGraph;

  private List<ParametricFactor> parametricFactors;
  private List<VariablePattern> factorPatterns;
  private List<String> factorNames;

  public ParametricFactorGraph(DynamicFactorGraph factorGraph,
      List<ParametricFactor> parametricFactors, List<VariablePattern> factorPatterns,
      List<String> factorNames) {
    Preconditions.checkArgument(parametricFactors.size() == factorPatterns.size());
    this.baseFactorGraph = factorGraph;
    this.parametricFactors = ImmutableList.copyOf(parametricFactors);
    this.factorPatterns = ImmutableList.copyOf(factorPatterns);
    this.factorNames = ImmutableList.copyOf(factorNames);
  }

  /**
   * Gets the variables over which the distributions in this family are defined.
   * All {@code DynamicFactorGraph}s returned by
   * {@link #getFactorGraphFromParameters(Object)} are defined over the same
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

  /**
   * Gets a {@code DynamicFactorGraph} which is the member of this family
   * indexed by {@code parameters}. Note that multiple values of
   * {@code parameters} may result in the same {@code FactorGraph}.
   * 
   * @param parameters
   * @return
   */
  public DynamicFactorGraph getFactorGraphFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    Preconditions.checkArgument(parameterList.size() == parametricFactors.size());
    List<PlateFactor> plateFactors = Lists.newArrayList();
    for (int i = 0; i < parameterList.size(); i++) {
      plateFactors.add(new ReplicatedFactor(
          parametricFactors.get(i).getFactorFromParameters(parameterList.get(i)),
          factorPatterns.get(i)));
    }
    return baseFactorGraph.addPlateFactors(plateFactors, factorNames);
  }

  /**
   * Gets a human-interpretable description of {@code parameters}. The returned
   * string has one parameter/description pair per line, with separators for
   * distinct factors.
   * 
   * @param parameters
   * @return
   */
  public String getParameterDescription(SufficientStatistics parameters) {
    StringBuilder builder = new StringBuilder();
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    Preconditions.checkArgument(parameterList.size() == parametricFactors.size());
    for (int i = 0; i < parameterList.size(); i++) {
      builder.append(parametricFactors.get(i).getParameterDescription(parameterList.get(i)));
    }
    return builder.toString();
  }

  /**
   * Gets a new, all-zero parameter vector for {@code this} family of
   * distributions.
   * 
   * @return
   */
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> sufficientStatistics = Lists.newArrayList();
    for (ParametricFactor factor : getParametricFactors()) {
      sufficientStatistics.add(factor.getNewSufficientStatistics());
    }
    return new ListSufficientStatistics(sufficientStatistics);
  }

  /**
   * Accumulates sufficient statistics (in {@code statistics}) for estimating a
   * model from {@code this} family based on a point distribution at
   * {@code assignment}. {@code count} is the number of times that
   * {@code assignment} has been observed in the training data, and acts as a
   * multiplier for the computed statistics {@code assignment} must contain a
   * value for all of the variables in {@code this.getVariables()}.
   * 
   * @param statistics
   * @param assignment
   * @param count
   */
  public void incrementSufficientStatistics(SufficientStatistics statistics,
      VariableNumMap variables, Assignment assignment, double count) {
    incrementSufficientStatistics(statistics, FactorMarginalSet.fromAssignment(
        variables, assignment), count);
  }

  /**
   * Computes a vector of sufficient statistics for {@code this} and accumulates
   * them in {@code statistics}. The statistics are computed from the
   * (conditional) marginal distribution {@code marginals}. {@code count} is the
   * number of times {@code marginals} has been observed in the training data.
   * 
   * @param statistics
   * @param marginals
   * @param count
   */ 
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
  
  public ParametricFactorGraphProto toProto() {
    ParametricFactorGraphProto.Builder builder = ParametricFactorGraphProto.newBuilder();

    IndexedList<Variable> variableTypeIndex = IndexedList.create();
    builder.setBaseFactorGraph(baseFactorGraph.toProtoBuilder(variableTypeIndex).build());
    
    for (ParametricFactor parametricFactor : parametricFactors) {
      builder.addParametricFactor(parametricFactor.toProto(variableTypeIndex));
    }

    for (VariablePattern pattern : factorPatterns) {
      builder.addFactorPattern(pattern.toProto(variableTypeIndex));
    }
    
    for (Variable variable : variableTypeIndex.items()) {
      builder.addVariableType(variable.toProto());
    }
    
    builder.addAllFactorName(factorNames);
    return builder.build();
  }
  
  public static ParametricFactorGraph fromProto(ParametricFactorGraphProto proto) {
    Preconditions.checkArgument(proto.hasBaseFactorGraph());
    Preconditions.checkArgument(proto.getParametricFactorCount() == proto.getFactorPatternCount());
    Preconditions.checkArgument(proto.getParametricFactorCount() == proto.getFactorNameCount());
    
    IndexedList<Variable> variableTypeIndex = IndexedList.create();
    for (VariableProto variableProto : proto.getVariableTypeList()) {
      variableTypeIndex.add(Variables.fromProto(variableProto));
    }
    DynamicFactorGraph baseFactorGraph = DynamicFactorGraph.fromProtoWithVariables(
        proto.getBaseFactorGraph(), variableTypeIndex);
    
    List<ParametricFactor> parametricFactors = Lists.newArrayList();
    for (ParametricFactorProto factorProto : proto.getParametricFactorList()) {
      parametricFactors.add(ParametricFactors.fromProto(factorProto, variableTypeIndex));
    }
    
    List<VariablePattern> variablePatterns = Lists.newArrayList();
    for (VariablePatternProto patternProto : proto.getFactorPatternList()) {
      variablePatterns.add(VariablePatterns.fromProto(patternProto, variableTypeIndex));
    }
    
    return new ParametricFactorGraph(baseFactorGraph, parametricFactors, variablePatterns, 
        proto.getFactorNameList());
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
