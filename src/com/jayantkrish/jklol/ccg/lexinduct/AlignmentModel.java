package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariableNumPattern;
import com.jayantkrish.jklol.util.Assignment;

public class AlignmentModel {

  private final DynamicFactorGraph factorGraph;
  
  private final String expressionPlateName;
  private final VariableNumMap wordPlateVar;
  private final VariableNumMap expressionPlateVar;
  
  public AlignmentModel(DynamicFactorGraph factorGraph, String expressionPlateName,
      VariableNumMap wordPlateVar, VariableNumMap expressionPlateVar) {
    this.factorGraph = Preconditions.checkNotNull(factorGraph);
    this.expressionPlateName = Preconditions.checkNotNull(expressionPlateName);
    this.wordPlateVar = Preconditions.checkNotNull(wordPlateVar);
    this.expressionPlateVar = Preconditions.checkNotNull(expressionPlateVar);
  }
  
  public FactorGraph getFactorGraph(AlignmentExample example) {
    // Assign the values of the observed expressions in this example.
    List<Assignment> treeAssignment = Lists.newArrayList();
    List<Expression> treeExpressions = Lists.newArrayList();
    example.getTree().getAllExpressions(treeExpressions);
    for (int i = 0; i < treeExpressions.size(); i++) {
      treeAssignment.add(expressionPlateVar.outcomeArrayToAssignment(treeExpressions.get(i)));
    }
    DynamicAssignment assignment = DynamicAssignment.createPlateAssignment(
        expressionPlateName, treeAssignment);

    // Add in a factor restricting the assignments to the words to contain
    // only the set of words in this example.
    Set<String> words = Sets.newHashSet(example.getWords());
    List<Assignment> wordAssignments = Lists.newArrayList();
    for (String word : words) {
      wordAssignments.add(wordPlateVar.outcomeArrayToAssignment(word));
    }
    
    TableFactor wordRestrictions = TableFactor.pointDistribution(wordPlateVar,
        wordAssignments.toArray(new Assignment[0]));

    // Replicate this restriction factor to every word variable.
    PlateFactor replicatedRestrictions = new ReplicatedFactor(wordRestrictions,
        VariableNumPattern.fromTemplateVariables(
            wordPlateVar, VariableNumMap.EMPTY, factorGraph.getVariables()));
    
    DynamicFactorGraph newFactorGraph = factorGraph.addPlateFactor(
        replicatedRestrictions, "word-restrictions");

    // Instantiate the plates with the expression and word variables.
    return newFactorGraph.conditional(assignment);
  }

  public FactorGraph getFactorGraphWithTreeConstraint(AlignmentExample example) {
    FactorGraph fg = getFactorGraph(example);
    
  }
}
