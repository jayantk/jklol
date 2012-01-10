package com.jayantkrish.jklol.cfg;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.AbstractConditionalFactor;
import com.jayantkrish.jklol.models.DiscreteObjectFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

public class BeamSearchCfgFactor extends AbstractConditionalFactor {
  
  private final VariableNumMap treeVariable;
  private final VariableNumMap terminalVariable;
  private final CfgParser parser;
  
  public BeamSearchCfgFactor(VariableNumMap treeVariable, VariableNumMap terminalVariable, 
      CfgParser parser) {
    super(treeVariable.union(terminalVariable));
    this.treeVariable = treeVariable;
    this.terminalVariable = terminalVariable;
    this.parser = parser;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    
    List<?> terminals = (List<?>) assignment.getValue(terminalVariable.getVariableNums().get(0));
    ParseTree tree = (ParseTree) assignment.getValue(treeVariable.getVariableNums().get(0));
    return parser.getProbability(terminals, tree);
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    return new BeamSearchCfgFactor(relabeling.apply(treeVariable), relabeling.apply(terminalVariable), parser);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    VariableNumMap conditionedVars = getVars().intersection(assignment.getVariableNums());
    if (conditionedVars.size() == 0) {
      return this;
    }
    Preconditions.checkArgument(conditionedVars.containsAll(terminalVariable));
    List<?> terminals = (List<?>) assignment.getValue(terminalVariable.getVariableNums().get(0));

    if (conditionedVars.containsAll(treeVariable)) {
      // If we also observe the tree, generate a factor over no variables with the appropriate
      // probability.
      ParseTree tree = (ParseTree) assignment.getValue(treeVariable.getVariableNums().get(0));
      return TableFactor.pointDistribution(VariableNumMap.emptyMap(), Assignment.EMPTY).product(
          parser.getProbability(terminals, tree));
    } else {
      // Find the "best" parse trees for the given terminals.
      List<ParseTree> trees = parser.beamSearch(terminals);
      Map<Assignment, Double> treeProbabilities = Maps.newHashMap();
      for (ParseTree tree : trees) {
        treeProbabilities.put(treeVariable.outcomeArrayToAssignment(tree), tree.getProbability());
      }
      return new DiscreteObjectFactor(treeVariable, treeProbabilities);
    }
  }
}
