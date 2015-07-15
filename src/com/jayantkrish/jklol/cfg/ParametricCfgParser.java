package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricCfgParser implements ParametricFamily<CfgParser> {
  private static final long serialVersionUID = 1L;
  
  // These are the variables contained in the two parametric factors for each
  // distribution in the cfg parser.
  private final VariableNumMap parentVar;
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar; 
  private final VariableNumMap terminalVar;
  private final VariableNumMap ruleTypeVar;

  private final ParametricFactor nonterminalFactor;
  private final ParametricFactor terminalFactor;

  private final int beamSize;
  private final boolean canSkipTerminals;

  private static final List<String> STATISTIC_NAMES = Arrays.asList("nonterminals", "terminals");

  public ParametricCfgParser(VariableNumMap parentVar, VariableNumMap leftVar,
      VariableNumMap rightVar, VariableNumMap terminalVar, VariableNumMap ruleTypeVar,
      ParametricFactor nonterminalFactor, ParametricFactor terminalFactor,
      int beamSize, boolean canSkipTerminals) {
    this.parentVar = parentVar;
    this.leftVar = leftVar;
    this.rightVar = rightVar;
    this.terminalVar = terminalVar;
    this.ruleTypeVar = ruleTypeVar;

    this.nonterminalFactor = nonterminalFactor;
    this.terminalFactor = terminalFactor;
    this.beamSize = beamSize;
    this.canSkipTerminals = canSkipTerminals;
  }

  @Override
  public CfgParser getModelFromParameters(SufficientStatistics parameters) {
    Preconditions.checkArgument(parameters instanceof ListSufficientStatistics);
    ListSufficientStatistics statisticsList = (ListSufficientStatistics) parameters;
    Preconditions.checkArgument(statisticsList.getStatistics().size() == 2);
    SufficientStatistics nonterminalStatistics = statisticsList.getStatistics().get(0);
    SufficientStatistics terminalStatistics = statisticsList.getStatistics().get(1);

    CfgParser parser = new CfgParser(parentVar, leftVar, rightVar, terminalVar, ruleTypeVar,
        (DiscreteFactor) nonterminalFactor.getModelFromParameters(nonterminalStatistics),
        (DiscreteFactor) terminalFactor.getModelFromParameters(terminalStatistics), beamSize,
        canSkipTerminals, null);
    return parser;
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    Preconditions.checkArgument(parameters instanceof ListSufficientStatistics);
    ListSufficientStatistics statisticsList = (ListSufficientStatistics) parameters;
    Preconditions.checkArgument(statisticsList.getStatistics().size() == 2);
    SufficientStatistics nonterminalStatistics = statisticsList.getStatistics().get(0);
    SufficientStatistics terminalStatistics = statisticsList.getStatistics().get(1);
    
    StringBuilder sb = new StringBuilder();
    sb.append("nonterminal distribution: \n");
    sb.append(nonterminalFactor.getParameterDescription(nonterminalStatistics, numFeatures));
    sb.append("terminal distribution: \n");
    sb.append(terminalFactor.getParameterDescription(terminalStatistics, numFeatures));
    return sb.toString();
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> statisticsList = Lists.newArrayList();
    statisticsList.add(nonterminalFactor.getNewSufficientStatistics());
    statisticsList.add(terminalFactor.getNewSufficientStatistics());
    return new ListSufficientStatistics(STATISTIC_NAMES, statisticsList);
  }
  
  public void incrementSufficientStatisticsFromParseChart(SufficientStatistics statistics,
      SufficientStatistics currentParameters, CfgParseChart chart, double count,
      double partitionFunction) {
    Preconditions.checkArgument(statistics instanceof ListSufficientStatistics);
    ListSufficientStatistics statisticsList = (ListSufficientStatistics) statistics;
    Preconditions.checkArgument(statisticsList.getStatistics().size() == 2);
    SufficientStatistics nonterminalStatistics = statisticsList.getStatistics().get(0);
    SufficientStatistics terminalStatistics = statisticsList.getStatistics().get(1);

    Preconditions.checkArgument(currentParameters instanceof ListSufficientStatistics);
    ListSufficientStatistics parameterList = (ListSufficientStatistics) currentParameters;
    Preconditions.checkArgument(parameterList.getStatistics().size() == 2);
    SufficientStatistics nonterminalParameters = parameterList.getStatistics().get(0);
    SufficientStatistics terminalParameters = parameterList.getStatistics().get(1);

    Factor binaryRuleDistribution = chart.getBinaryRuleExpectations();
    nonterminalFactor.incrementSufficientStatisticsFromMarginal(nonterminalStatistics,
        nonterminalParameters, binaryRuleDistribution, Assignment.EMPTY, count, partitionFunction);
    
    Factor terminalRuleDistribution = chart.getTerminalRuleExpectations();
    terminalFactor.incrementSufficientStatisticsFromMarginal(terminalStatistics,
        terminalParameters, terminalRuleDistribution, Assignment.EMPTY, count, partitionFunction);
  }

  public void incrementSufficientStatisticsFromParseTree(SufficientStatistics statistics,
      SufficientStatistics currentParameters, CfgParseTree parse, double count) {
    Preconditions.checkArgument(statistics instanceof ListSufficientStatistics);
    ListSufficientStatistics statisticsList = (ListSufficientStatistics) statistics;
    Preconditions.checkArgument(statisticsList.getStatistics().size() == 2);
    SufficientStatistics nonterminalStatistics = statisticsList.getStatistics().get(0);
    SufficientStatistics terminalStatistics = statisticsList.getStatistics().get(1);

    Preconditions.checkArgument(currentParameters instanceof ListSufficientStatistics);
    ListSufficientStatistics parameterList = (ListSufficientStatistics) currentParameters;
    Preconditions.checkArgument(parameterList.getStatistics().size() == 2);
    SufficientStatistics nonterminalParameters = parameterList.getStatistics().get(0);
    SufficientStatistics terminalParameters = parameterList.getStatistics().get(1);
    accumulateSufficientStatistics(parse, nonterminalStatistics, terminalStatistics, 
        nonterminalParameters, terminalParameters, count);
  }

  /**
   * Accumulates sufficient statistics for the production rules in {@code tree}.
   * Each occurrence of a production rule increments the corresponding
   * sufficient statistics (for the rule) by {@code weight}.
   * 
   * @param tree
   * @param nonterminalStatistics
   * @param terminalStatistics
   * @param nonterminalParameters
   * @param terminalParameters
   * @param weight
   */
  private void accumulateSufficientStatistics(CfgParseTree tree,
      SufficientStatistics nonterminalStatistics, SufficientStatistics terminalStatistics,
      SufficientStatistics nonterminalParameters, SufficientStatistics terminalParameters,
      double weight) {
    if (tree == CfgParseTree.EMPTY) {
      return;
    }

    if (tree.isTerminal()) {
      Assignment terminalRule = parentVar.outcomeArrayToAssignment(tree.getRoot())
          .union(terminalVar.outcomeArrayToAssignment(tree.getTerminalProductions()))
          .union(ruleTypeVar.outcomeArrayToAssignment(tree.getRuleType()));
      terminalFactor.incrementSufficientStatisticsFromAssignment(terminalStatistics,
          terminalParameters, terminalRule, weight);
      // System.out.println(weight + " " + terminalRule);
    } else {
      Assignment nonterminalRule = parentVar.outcomeArrayToAssignment(tree.getRoot())
          .union(leftVar.outcomeArrayToAssignment(tree.getLeft().getRoot()))
          .union(rightVar.outcomeArrayToAssignment(tree.getRight().getRoot()))
          .union(ruleTypeVar.outcomeArrayToAssignment(tree.getRuleType()));
      nonterminalFactor.incrementSufficientStatisticsFromAssignment(nonterminalStatistics,
          terminalParameters, nonterminalRule, weight);
      // System.out.println(weight + " " + nonterminalRule);
      accumulateSufficientStatistics(tree.getLeft(), nonterminalStatistics, terminalStatistics,
          nonterminalParameters, terminalParameters, weight);
      accumulateSufficientStatistics(tree.getRight(), nonterminalStatistics, terminalStatistics,
          nonterminalParameters, terminalParameters, weight);
    }
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    throw new UnsupportedOperationException("not implemented.");
  }
}
