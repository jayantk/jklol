package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraphBuilder;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariableNumPattern;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;

public class AlignmentModel {

  private final DynamicFactorGraph factorGraph;
  
  private final String expressionPlateName;
  private final VariableNumMap wordPlateVar;
  private final VariableNumMap expressionPlateVar;
  
  private final String booleanPlateName;
  private final VariableNumMap booleanPlateVar;
  
  private final boolean useTreeConstraint;

  private final VariableNumMap wordActiveWord, wordActiveBoolean;
  private final Factor wordActiveFactor;
  
  private final VariableNumMap input1, input2, output;
  private final Factor andFactor;
  private final Factor orFactor;
  
  public AlignmentModel(DynamicFactorGraph factorGraph, String expressionPlateName,
      VariableNumMap wordPlateVar, VariableNumMap expressionPlateVar, String booleanPlateName,
      VariableNumMap booleanPlateVar, boolean useTreeConstraint) {
    this.factorGraph = Preconditions.checkNotNull(factorGraph);

    this.expressionPlateName = Preconditions.checkNotNull(expressionPlateName);
    this.wordPlateVar = Preconditions.checkNotNull(wordPlateVar);
    this.expressionPlateVar = Preconditions.checkNotNull(expressionPlateVar);

    this.booleanPlateName = booleanPlateName;
    this.booleanPlateVar = booleanPlateVar;
    
    this.useTreeConstraint = useTreeConstraint;

    wordActiveBoolean = booleanPlateVar.relabelVariableNums(new int[] {0});
    wordActiveWord = wordPlateVar.relabelVariableNums(new int[] {1});
    DiscreteFactor nullIndicator = TableFactor.pointDistribution(wordActiveWord,
        wordActiveWord.outcomeArrayToAssignment(ParametricAlignmentModel.NULL_WORD));
    DiscreteFactor otherWordIndicator = TableFactor.unity(wordActiveWord).add(nullIndicator.product(-1));
    
    DiscreteFactor nullIndicatorExpanded = nullIndicator.outerProduct(TableFactor
        .pointDistribution(wordActiveBoolean, wordActiveBoolean.outcomeArrayToAssignment("F")));
    DiscreteFactor otherWordIndicatorExpanded = otherWordIndicator.outerProduct(TableFactor
        .pointDistribution(wordActiveBoolean, wordActiveBoolean.outcomeArrayToAssignment("T")));
    this.wordActiveFactor = nullIndicatorExpanded.add(otherWordIndicatorExpanded);

    input1 = booleanPlateVar.relabelVariableNums(new int[] {0});
    input2 = booleanPlateVar.relabelVariableNums(new int[] {1});
    output = booleanPlateVar.relabelVariableNums(new int[] {2});
    
    VariableNumMap bools = VariableNumMap.unionAll(input1, input2, output);
    this.andFactor = TableFactor.pointDistribution(bools, 
        bools.outcomeArrayToAssignment("T", "T", "T"),
        bools.outcomeArrayToAssignment("F", "F", "F"));

    this.orFactor = TableFactor.pointDistribution(bools, 
        bools.outcomeArrayToAssignment("F", "F", "F"),
        bools.outcomeArrayToAssignment("T", "F", "T"),
        bools.outcomeArrayToAssignment("F", "T", "T"));
  }
  
  public void getBestAlignment(AlignmentExample example) {
    Pair<FactorGraph, AugmentedExpressionTree> pair = getFactorGraphWithTreeConstraint(example);
    FactorGraph fg = pair.getLeft();
    AugmentedExpressionTree tree = pair.getRight();
    JunctionTree jt = new JunctionTree(true);
    MaxMarginalSet maxMarginals = jt.computeMaxMarginals(fg);

    Assignment best = maxMarginals.getNthBestAssignment(0);

    AugmentedExpressionTree pruned = tree.pruneWithAssignment(best);
    System.out.println(pruned);
  }
  
  public FactorGraph getFactorGraph(AlignmentExample example) {
    if (useTreeConstraint) {
      return getFactorGraphWithTreeConstraint(example).getLeft();
    } else {
      return getFactorGraphWithoutTreeConstaint(example);
    }
  }

  private FactorGraph getFactorGraphWithoutTreeConstaint(AlignmentExample example) {
    // Assign the values of the observed expressions in this example.
    List<Assignment> treeAssignment = Lists.newArrayList();
    List<Expression> treeExpressions = Lists.newArrayList();
    example.getTree().getAllExpressions(treeExpressions);
    for (int i = 0; i < treeExpressions.size(); i++) {
      treeAssignment.add(expressionPlateVar.outcomeArrayToAssignment(treeExpressions.get(i)));
    }
    DynamicAssignment assignment = DynamicAssignment.createPlateAssignment(
        expressionPlateName, treeAssignment);
    DynamicAssignment booleanAssignment = DynamicAssignment.createPlateAssignment(
        booleanPlateName, Collections.<Assignment>emptyList());

    DynamicFactorGraph newFactorGraph = getBaseFactorGraphBuilder(example).build();

    // Instantiate the plates with the expression and word variables.
    return newFactorGraph.conditional(assignment.union(booleanAssignment));
  }

  private Pair<FactorGraph, AugmentedExpressionTree> getFactorGraphWithTreeConstraint(
      AlignmentExample example) {
    DynamicFactorGraphBuilder fg = getBaseFactorGraphBuilder(example);

    List<Assignment> assignmentAccumulator = Lists.newArrayList();
    List<Assignment> booleanAssignmentAccumulator = Lists.newArrayList();
    AugmentedExpressionTree tree = buildTreeConstraint(example.getTree(), fg,
        assignmentAccumulator, booleanAssignmentAccumulator);
    
    DynamicAssignment assignment = DynamicAssignment.createPlateAssignment(
        expressionPlateName, assignmentAccumulator);
    DynamicAssignment booleanAssignment = DynamicAssignment.createPlateAssignment(booleanPlateName,
        booleanAssignmentAccumulator);

    // Instantiate the plates with the expression and word variables.
    FactorGraph wholeFactorGraph = fg.build().conditional(assignment.union(booleanAssignment));
    
    return Pair.of(wholeFactorGraph.conditional(tree.getVar().outcomeArrayToAssignment("T")),
        tree);
  }

  private AugmentedExpressionTree buildTreeConstraint(ExpressionTree tree,
      DynamicFactorGraphBuilder builder, List<Assignment> assignments,
      List<Assignment> booleanAssignments) {

    int expressionPlateIndex = assignments.size();
    assignments.add(expressionPlateVar.outcomeArrayToAssignment(tree.getExpression()));
    
    // Add a variable to the graphical model that determines whether
    // this expression is active.
    VariableNumMap wordActiveVar = getNextBooleanVar(builder, booleanAssignments);
    VariableNumMap wordVar = builder.getVariables().instantiatePlateFixedVars(
        expressionPlateName, expressionPlateIndex, wordPlateVar.getVariableNumsArray());
    VariableNumMap factorVars = wordActiveVar.union(wordVar);
    // Add the corresponding factor constraining the word generation
    // distribution based on the activity of this expression.
    VariableRelabeling relabeling = VariableRelabeling.createFromVariables(
        wordActiveWord, wordVar).union(VariableRelabeling.createFromVariables(wordActiveBoolean, wordActiveVar));
    Factor activeFactor = wordActiveFactor.relabelVariables(relabeling);
    builder.addUnreplicatedFactor("word-active" + expressionPlateIndex, activeFactor, factorVars);

    if (!tree.hasChildren()) {
      return new AugmentedExpressionTree(wordActiveVar, tree.getExpression(),
          Collections.<AugmentedExpressionTree>emptyList(),
          Collections.<AugmentedExpressionTree>emptyList(), null);
    } else {
      List<ExpressionTree> lefts = tree.getLeftChildren();
      List<ExpressionTree> rights = tree.getRightChildren();

      VariableNumMap orVar = wordActiveVar;
      List<AugmentedExpressionTree> newLefts = Lists.newArrayList();
      List<AugmentedExpressionTree> newRights = Lists.newArrayList();
      for (int i = 0; i < lefts.size(); i++) {
        AugmentedExpressionTree leftTree = buildTreeConstraint(lefts.get(i), builder, assignments, booleanAssignments);
        AugmentedExpressionTree rightTree = buildTreeConstraint(rights.get(i), builder, assignments, booleanAssignments);
        
        newLefts.add(leftTree);
        newRights.add(rightTree);
        
        VariableNumMap leftRootVar = leftTree.getVar();
        VariableNumMap rightRootVar = rightTree.getVar();

        VariableNumMap andVar = getNextBooleanVar(builder, booleanAssignments);
        VariableNumMap myBools = VariableNumMap.unionAll(leftRootVar, rightRootVar, andVar);
        relabeling = VariableRelabeling.createFromVariables(input1, leftRootVar).union(
            VariableRelabeling.createFromVariables(input2, rightRootVar)).union(
                VariableRelabeling.createFromVariables(output, andVar));

        builder.addUnreplicatedFactor("and-" + expressionPlateIndex + "-" + i,
            andFactor.relabelVariables(relabeling), myBools);
        VariableNumMap nextOrVar = getNextBooleanVar(builder, booleanAssignments);

        relabeling = VariableRelabeling.createFromVariables(input1, orVar).union(
            VariableRelabeling.createFromVariables(input2, andVar)).union(
                VariableRelabeling.createFromVariables(output, nextOrVar));
        myBools = VariableNumMap.unionAll(andVar, orVar, nextOrVar);

        builder.addUnreplicatedFactor("or-" + expressionPlateIndex + "-" + i,
            orFactor.relabelVariables(relabeling), myBools);
        orVar = nextOrVar;
      }

      return new AugmentedExpressionTree(orVar, tree.getExpression(), newLefts, newRights, null);
    }
  }

  private VariableNumMap getNextBooleanVar(DynamicFactorGraphBuilder builder, List<Assignment> assignments) {
    int booleanPlateIndex = assignments.size();
    assignments.add(Assignment.EMPTY);
    return builder.getVariables().instantiatePlateFixedVars(booleanPlateName, booleanPlateIndex, null);
  }
  
  private DynamicFactorGraphBuilder getBaseFactorGraphBuilder(AlignmentExample example) {
    // Add in a factor restricting the assignments to the words to contain
    // only the set of words in this example.
    Set<String> words = Sets.newHashSet(example.getWords());
    if (useTreeConstraint) {
      words.add(ParametricAlignmentModel.NULL_WORD);
    }
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

    return newFactorGraph.toBuilder();
  }
  
  private static class AugmentedExpressionTree {
    private final VariableNumMap var;
    private final Expression expression;
    
    private final List<AugmentedExpressionTree> lefts;
    private final List<AugmentedExpressionTree> rights;
    
    private final String word;

    public AugmentedExpressionTree(VariableNumMap var, Expression expression,
        List<AugmentedExpressionTree> lefts, List<AugmentedExpressionTree> rights,
        String word) {
      this.var = Preconditions.checkNotNull(var);
      this.expression = Preconditions.checkNotNull(expression);
      this.lefts = Preconditions.checkNotNull(lefts);
      this.rights = Preconditions.checkNotNull(rights);
      this.word = word;
    }

    public VariableNumMap getVar() {
      return var;
    }
    public Expression getExpression() {
      return expression;
    }
    public List<AugmentedExpressionTree> getLefts() {
      return lefts;
    }
    public List<AugmentedExpressionTree> getRights() {
      return rights;
    }
    
    public AugmentedExpressionTree pruneWithAssignment(Assignment assignment) {
      if (var.assignmentToOutcome(assignment).equals(Arrays.asList("T"))) {
        
        List<AugmentedExpressionTree> newLefts = Lists.newArrayList();
        List<AugmentedExpressionTree> newRights = Lists.newArrayList();
        for (int i = 0; i < lefts.size(); i++) {
          AugmentedExpressionTree left = lefts.get(i).pruneWithAssignment(assignment);
          AugmentedExpressionTree right = rights.get(i).pruneWithAssignment(assignment);

          Preconditions.checkState((left == null && right == null) ||
              (left != null && right != null));
          
          if (left != null) {
            newLefts.add(left);
            newRights.add(right);
          }
        }
        return new AugmentedExpressionTree(var, expression, newLefts, newRights, word);
      } else {
        return null;
      }
    }
    
    public String toString() {
      StringBuilder sb = new StringBuilder();
      toStringHelper(this, sb, 0);
      return sb.toString();
    }

    private static void toStringHelper(AugmentedExpressionTree tree, StringBuilder sb, int depth) {
      for (int i = 0 ; i < depth; i++) {
        sb.append(" ");
      }
      sb.append(tree.expression);
      sb.append("\n");

      for (int i = 0; i < tree.lefts.size(); i++) {
        toStringHelper(tree.lefts.get(i), sb, depth + 2);      
        toStringHelper(tree.rights.get(i), sb, depth + 2);
      }
    }
  }
}
