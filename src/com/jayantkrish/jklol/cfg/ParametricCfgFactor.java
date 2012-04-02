package com.jayantkrish.jklol.cfg;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteFactor;
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
    
  private final ParametricFactor<SufficientStatistics> nonterminalFactor;
  private final ParametricFactor<SufficientStatistics> terminalFactor;
  
  private final Function<Object, List<Object>> terminalFunction;
  private final Predicate<? super ParseTree> validTreeFilter;

  private final int beamSize;
  private final boolean canSkipTerminals;

  public ParametricCfgFactor(VariableNumMap parentVar, VariableNumMap leftVar,
      VariableNumMap rightVar, VariableNumMap terminalVar, VariableNumMap ruleTypeVar,
      VariableNumMap treeVar, VariableNumMap inputVar,
      ParametricFactor<SufficientStatistics> nonterminalFactor,
      ParametricFactor<SufficientStatistics> terminalFactor,
      Function<Object, List<Object>> terminalFunction, Predicate<? super ParseTree> validTreeFilter,
      int beamSize, boolean canSkipTerminals) {
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
    this.canSkipTerminals = canSkipTerminals;
    this.terminalFunction = terminalFunction;
    this.validTreeFilter = validTreeFilter;
  }

  @Override
  public Factor getFactorFromParameters(SufficientStatistics parameters) {
    Preconditions.checkArgument(parameters instanceof ListSufficientStatistics);
    ListSufficientStatistics statisticsList = (ListSufficientStatistics) parameters;
    Preconditions.checkArgument(statisticsList.getStatistics().size() == 2);
    SufficientStatistics nonterminalStatistics = statisticsList.getStatistics().get(0);
    SufficientStatistics terminalStatistics = statisticsList.getStatistics().get(1);

    CfgParser parser = new CfgParser(parentVar, leftVar, rightVar, terminalVar, ruleTypeVar,
        (DiscreteFactor) nonterminalFactor.getFactorFromParameters(nonterminalStatistics),
        (DiscreteFactor) terminalFactor.getFactorFromParameters(terminalStatistics), beamSize, canSkipTerminals);
    return new BeamSearchCfgFactor(treeVar, inputVar, parser, terminalFunction, validTreeFilter);
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters) { 
    throw new UnsupportedOperationException();
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
    
    Preconditions.checkArgument(statistics instanceof ListSufficientStatistics);
    ListSufficientStatistics statisticsList = (ListSufficientStatistics) statistics;
    Preconditions.checkArgument(statisticsList.getStatistics().size() == 2);
    SufficientStatistics nonterminalStatistics = statisticsList.getStatistics().get(0);
    SufficientStatistics terminalStatistics = statisticsList.getStatistics().get(1);

    if (conditionalAssignment.containsAll(treeVar.getVariableNums())) {
      ParseTree tree = (ParseTree) conditionalAssignment.getValue(treeVar.getVariableNums().get(0));
      
      accumulateSufficientStatistics(tree, nonterminalStatistics, terminalStatistics, count);
    } else {
      DiscreteObjectFactor objectMarginal = (DiscreteObjectFactor) marginal;
      
      for (Assignment assignment : objectMarginal.assignments()) {
        ParseTree tree = (ParseTree) assignment.getValue(treeVar.getVariableNums().get(0));
        
        accumulateSufficientStatistics(tree, nonterminalStatistics, terminalStatistics, 
            count * objectMarginal.getUnnormalizedProbability(assignment) / partitionFunction);
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
    if (tree == ParseTree.EMPTY) {
      return;
    }

    if (tree.isTerminal()) {
      Assignment terminalRule = parentVar.outcomeArrayToAssignment(tree.getRoot())
          .union(terminalVar.outcomeArrayToAssignment(tree.getTerminalProductions()))
          .union(ruleTypeVar.outcomeArrayToAssignment(tree.getRuleType()));
      terminalFactor.incrementSufficientStatisticsFromAssignment(terminalStatistics, terminalRule,
          weight);
      System.out.println(weight + " " + terminalRule);
    } else {
      Assignment nonterminalRule = parentVar.outcomeArrayToAssignment(tree.getRoot())
          .union(leftVar.outcomeArrayToAssignment(tree.getLeft().getRoot()))
          .union(rightVar.outcomeArrayToAssignment(tree.getRight().getRoot()))
          .union(ruleTypeVar.outcomeArrayToAssignment(tree.getRuleType()));
      nonterminalFactor.incrementSufficientStatisticsFromAssignment(nonterminalStatistics,
          nonterminalRule, weight);
      System.out.println(weight + " " + nonterminalRule);
      accumulateSufficientStatistics(tree.getLeft(), nonterminalStatistics, terminalStatistics, weight);
      accumulateSufficientStatistics(tree.getRight(), nonterminalStatistics, terminalStatistics, weight);
    }
  }
}
