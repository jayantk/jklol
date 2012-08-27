package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

public class CptCfgFactor extends AbstractParametricFactor {

  private final VariableNumMap parentVar;
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar;
  private final VariableNumMap terminalVar;
  private final VariableNumMap ruleTypeVar;
  
  private final VariableNumMap rootVar;
  private final VariableNumMap childVar;
  
  private ParametricFactor nonterminalFactor;
  private ParametricFactor terminalFactor;

  private static final List<String> STATISTIC_NAMES = Arrays.asList("nonterminals", "terminals");
  
  public CptCfgFactor(VariableNumMap parentVar, VariableNumMap leftVar, VariableNumMap rightVar, 
      VariableNumMap terminalVar, VariableNumMap ruleTypeVar, VariableNumMap rootVar, 
      VariableNumMap childVar, ParametricFactor nonterminalFactor, 
      ParametricFactor terminalFactor) {
    super(rootVar.union(childVar));
    this.parentVar = parentVar;
    this.leftVar = leftVar;
    this.rightVar = rightVar;
    this.terminalVar = terminalVar;
    this.ruleTypeVar = ruleTypeVar;
    this.rootVar = rootVar;
    this.childVar = childVar;
    this.nonterminalFactor = nonterminalFactor;
    this.terminalFactor = terminalFactor;
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
        (DiscreteFactor) terminalFactor.getFactorFromParameters(terminalStatistics), 0, false);
    return new CfgFactor(rootVar, childVar, parser);
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) { 
    throw new UnsupportedOperationException();
  }
  
  @Override
  public String getParameterDescriptionXML(SufficientStatistics parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    List<SufficientStatistics> statisticsList = Lists.newArrayList();
    statisticsList.add(nonterminalFactor.getNewSufficientStatistics());
    statisticsList.add(terminalFactor.getNewSufficientStatistics());
    return new ListSufficientStatistics(STATISTIC_NAMES, statisticsList);
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      Assignment assignment, double count) {
    throw new UnsupportedOperationException("Cannot compute statistics from an assignment.");
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      Factor marginal, Assignment assignment, double count, double partitionFunction) {
    Preconditions.checkArgument(marginal instanceof CfgFactor);
    ParseChart chart = ((CfgFactor) marginal).getMarginalChart(true);
    Factor nonterminalMarginal = chart.getBinaryRuleExpectations();
    Factor terminalMarginal = chart.getTerminalRuleExpectations();
        
    Preconditions.checkArgument(statistics instanceof ListSufficientStatistics);
    ListSufficientStatistics statisticsList = (ListSufficientStatistics) statistics;
    Preconditions.checkArgument(statisticsList.getStatistics().size() == 2);
    SufficientStatistics nonterminalStatistics = statisticsList.getStatistics().get(0);
    SufficientStatistics terminalStatistics = statisticsList.getStatistics().get(1);
    
    // Update binary/terminal rule counts
    nonterminalFactor.incrementSufficientStatisticsFromMarginal(nonterminalStatistics, 
        nonterminalMarginal, Assignment.EMPTY, count, partitionFunction);
    terminalFactor.incrementSufficientStatisticsFromMarginal(terminalStatistics, 
        terminalMarginal, Assignment.EMPTY, count, partitionFunction);
  }
  
  @Override
  public ParametricFactorProto toProto(IndexedList<Variable> variableTypeIndex) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
}
