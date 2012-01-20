package com.jayantkrish.jklol.cfg;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteObjectFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricCfgFactor extends AbstractParametricFactor<SufficientStatistics> {

  // These are the variables contained in the two parametric factors for each
  // distribution
  // in the cfg parser.
  private final VariableNumMap parentVar;
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar;
  private final VariableNumMap terminalVar;
  private final VariableNumMap ruleTypeVar;

  // These variables are present in the factor graph.
  private final VariableNumMap treeVar;
  private final VariableNumMap inputVar;

  private final int beamSize;

  private ParametricFactor<SufficientStatistics> nonterminalFactor;
  private ParametricFactor<SufficientStatistics> terminalFactor;

  public ParametricCfgFactor(VariableNumMap parentVar, VariableNumMap leftVar,
      VariableNumMap rightVar, VariableNumMap terminalVar, VariableNumMap ruleTypeVar,
      VariableNumMap treeVar, VariableNumMap inputVar,
      ParametricFactor<SufficientStatistics> nonterminalFactor,
      ParametricFactor<SufficientStatistics> terminalFactor,
      int beamSize) {
    super(treeVar.union(inputVar));
    this.parentVar = parentVar;
    this.leftVar = leftVar;
    this.rightVar = rightVar;
    this.terminalVar = terminalVar;
    this.ruleTypeVar = ruleTypeVar;

    this.treeVar = treeVar;
    this.inputVar = inputVar;

    this.nonterminalFactor = nonterminalFactor;
    this.terminalFactor = terminalFactor;
    this.beamSize = beamSize;
  }

  @Override
  public Factor getFactorFromParameters(SufficientStatistics parameters) {
    Preconditions.checkArgument(parameters instanceof ListSufficientStatistics);
    ListSufficientStatistics statisticsList = (ListSufficientStatistics) parameters;
    Preconditions.checkArgument(statisticsList.getStatistics().size() == 2);
    SufficientStatistics nonterminalStatistics = statisticsList.getStatistics().get(0);
    SufficientStatistics terminalStatistics = statisticsList.getStatistics().get(1);

    CfgParser parser = new CfgParser(parentVar, leftVar, rightVar, terminalVar, ruleTypeVar,
        nonterminalFactor.getFactorFromParameters(nonterminalStatistics),
        terminalFactor.getFactorFromParameters(terminalStatistics), beamSize);
    return new BeamSearchCfgFactor(treeVar, inputVar, parser);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> statisticsList = Lists.newArrayList();
    statisticsList.add(nonterminalFactor.getNewSufficientStatistics());
    statisticsList.add(terminalFactor.getNewSufficientStatistics());
    return new ListSufficientStatistics(statisticsList);
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      Assignment assignment, double count) {
    incrementSufficientStatisticsFromMarginal(statistics, null, assignment, count, 1.0);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    Preconditions.checkArgument(conditionalAssignment.containsAll(inputVar.getVariableNums()));
    
    List<?> terminals = (List<?>) conditionalAssignment.getValue(inputVar.getVariableNums().get(0));
    
    Preconditions.checkArgument(statistics instanceof ListSufficientStatistics);
    ListSufficientStatistics statisticsList = (ListSufficientStatistics) statistics;
    Preconditions.checkArgument(statisticsList.getStatistics().size() == 2);
    SufficientStatistics nonterminalStatistics = statisticsList.getStatistics().get(0);
    SufficientStatistics terminalStatistics = statisticsList.getStatistics().get(1);

    if (conditionalAssignment.containsAll(treeVar.getVariableNums())) {
      ParseTree tree = (ParseTree) conditionalAssignment.getValue(treeVar.getVariableNums().get(0));
      if (tree.getTerminalProductions().equals(terminals)) {
        accumulateSufficientStatistics(tree, nonterminalStatistics, terminalStatistics, count);
      }
    } else {
      DiscreteObjectFactor objectMarginal = (DiscreteObjectFactor) marginal;
      
      for (Assignment assignment : objectMarginal.assignments()) {
        ParseTree tree = (ParseTree) assignment.getValue(treeVar.getVariableNums().get(0));
        
        if (tree.getTerminalProductions().equals(terminals)) {
          accumulateSufficientStatistics(tree, nonterminalStatistics, terminalStatistics, 
              count * objectMarginal.getUnnormalizedProbability(assignment) / partitionFunction);
        }
      }
    }
  }

  /**
   * Accumulates sufficient statistics for the production rules in {@code tree}.
   * Each occurrence of a production rule increments the corresponding
   * sufficient statistics (for the rule) by {@code weight}.
   * 
   * @param tree
   * @param nonterminalFactor
   * @param terminalFactor
   * @param weight
   */
  private void accumulateSufficientStatistics(ParseTree tree,
      SufficientStatistics nonterminalStatistics,
      SufficientStatistics terminalStatistics, double weight) {
    if (tree.isTerminal()) {
      Assignment terminalRule = parentVar.outcomeArrayToAssignment(tree.getRoot())
          .union(terminalVar.outcomeArrayToAssignment(tree.getTerminalProductions()))
          .union(ruleTypeVar.outcomeArrayToAssignment(tree.getRuleType()));
      terminalFactor.incrementSufficientStatisticsFromAssignment(terminalStatistics, terminalRule,
          weight);
    } else {
      Assignment nonterminalRule = parentVar.outcomeArrayToAssignment(tree.getRoot())
          .union(leftVar.outcomeArrayToAssignment(tree.getLeft().getRoot()))
          .union(rightVar.outcomeArrayToAssignment(tree.getRight().getRoot()))
          .union(ruleTypeVar.outcomeArrayToAssignment(tree.getRuleType()));
      nonterminalFactor.incrementSufficientStatisticsFromAssignment(nonterminalStatistics,
          nonterminalRule, weight);
      accumulateSufficientStatistics(tree.getLeft(), nonterminalStatistics, terminalStatistics, weight);
      accumulateSufficientStatistics(tree.getRight(), nonterminalStatistics, terminalStatistics, weight);
    }
  }
}
