package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
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

  private final VariableNumMap expressionPlateVar;
  private final VariableNumMap featurePlateVar;
  private final VariableNumMap wordPlateVar;
  
  private final String booleanPlateName;
  private final VariableNumMap booleanPlateVar;
  
  private final boolean useTreeConstraint;

  private final VariableNumMap wordActiveWord, wordActiveBoolean;
  private final Factor wordActiveFactor;
  
  private final VariableNumMap input1, input2, output;
  private final Factor andFactor;
  private final Factor orFactor;
  
  public AlignmentModel(DynamicFactorGraph factorGraph, String expressionPlateName,
      VariableNumMap expressionPlateVar, VariableNumMap featurePlateVar,
      VariableNumMap wordPlateVar, String booleanPlateName,
      VariableNumMap booleanPlateVar, boolean useTreeConstraint) {
    this.factorGraph = Preconditions.checkNotNull(factorGraph);

    this.expressionPlateName = Preconditions.checkNotNull(expressionPlateName);
    this.expressionPlateVar = Preconditions.checkNotNull(expressionPlateVar);
    this.featurePlateVar = Preconditions.checkNotNull(featurePlateVar);
    this.wordPlateVar = Preconditions.checkNotNull(wordPlateVar);

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
    
    // The probability of the assignment T,T,T is set to 1 - epsilon
    // to slightly prefer larger assignments. In cases where a single word
    // is likely to align to any node of a tree of the form A -> (B, C),
    // this setting means the alignment to A is preferable.
    VariableNumMap bools = VariableNumMap.unionAll(input1, input2, output);
    this.andFactor = TableFactor.pointDistribution(bools, 
        bools.outcomeArrayToAssignment("F", "F", "F")).add(TableFactor.pointDistribution(bools, 
        bools.outcomeArrayToAssignment("T", "T", "T")).product(0.9999));

    this.orFactor = TableFactor.pointDistribution(bools, 
        bools.outcomeArrayToAssignment("F", "F", "F"),
        bools.outcomeArrayToAssignment("T", "F", "T"),
        bools.outcomeArrayToAssignment("F", "T", "T"));
  }

  public AlignedExpressionTree getBestAlignment(AlignmentExample example) {
    Pair<FactorGraph, AlignmentTree> pair = getFactorGraphWithTreeConstraint(example);
    FactorGraph fg = pair.getLeft();
    AlignmentTree tree = pair.getRight();
    JunctionTree jt = new JunctionTree(true);
    MaxMarginalSet maxMarginals = jt.computeMaxMarginals(fg);

    Assignment best = maxMarginals.getNthBestAssignment(0);

    return tree.decodeAlignment(best, example.getWords());
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

  private Pair<FactorGraph, AlignmentTree> getFactorGraphWithTreeConstraint(
      AlignmentExample example) {
    DynamicFactorGraphBuilder fg = getBaseFactorGraphBuilder(example);

    List<Assignment> assignmentAccumulator = Lists.newArrayList();
    List<Assignment> booleanAssignmentAccumulator = Lists.newArrayList();
    AlignmentTree tree = buildTreeConstraint(example.getTree(), fg,
        assignmentAccumulator, booleanAssignmentAccumulator);
    
    DynamicAssignment assignment = DynamicAssignment.createPlateAssignment(
        expressionPlateName, assignmentAccumulator);
    DynamicAssignment booleanAssignment = DynamicAssignment.createPlateAssignment(booleanPlateName,
        booleanAssignmentAccumulator);

    // Instantiate the plates with the expression and word variables.
    FactorGraph wholeFactorGraph = fg.build().conditional(assignment.union(booleanAssignment));

    Assignment treeAssignment = tree.getVar().outcomeArrayToAssignment("T");
    return Pair.of(wholeFactorGraph.conditional(treeAssignment), tree);
  }

  /**
   * Adds factors and variables for the tree structured logical
   * form decomposition constraint to {@code builder}. Furthermore,
   * any assignments to the generated variables are added to 
   * {@code expressionAssignments} and {@code booleanAssignments}.
   * 
   * @param tree
   * @param builder
   * @param expressionAssignments
   * @param booleanAssignments
   * @return
   */
  private AlignmentTree buildTreeConstraint(ExpressionTree tree, 
      DynamicFactorGraphBuilder builder, List<Assignment> expressionAssignments,
      List<Assignment> booleanAssignments) {

    int expressionPlateIndex = expressionAssignments.size();
    Assignment assignment = expressionPlateVar.outcomeArrayToAssignment(tree.getExpression())
        .union(featurePlateVar.outcomeArrayToAssignment(tree.getExpressionFeatures()));
    expressionAssignments.add(assignment);
    
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
      return new AlignmentTree(wordActiveVar, tree.getExpression(), tree.getNumAppliedArguments(),
          Collections.<AlignmentTree>emptyList(), Collections.<AlignmentTree>emptyList(),
          wordVar, wordActiveVar);
    } else {
      List<ExpressionTree> lefts = tree.getLeftChildren();
      List<ExpressionTree> rights = tree.getRightChildren();

      VariableNumMap orVar = wordActiveVar;
      List<AlignmentTree> newLefts = Lists.newArrayList();
      List<AlignmentTree> newRights = Lists.newArrayList();
      for (int i = 0; i < lefts.size(); i++) {
        AlignmentTree leftTree = buildTreeConstraint(lefts.get(i), builder, expressionAssignments, booleanAssignments);
        AlignmentTree rightTree = buildTreeConstraint(rights.get(i), builder, expressionAssignments, booleanAssignments);
        
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

      return new AlignmentTree(orVar, tree.getExpression(), tree.getNumAppliedArguments(), 
          newLefts, newRights, wordVar, wordActiveVar);
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
  
  private static class AlignmentTree {
    private final VariableNumMap var;
    private final Expression expression;

    // Number of arguments of expression that get
    // applied in this tree.
    private final int numAppliedArguments;
    
    private final List<AlignmentTree> lefts;
    private final List<AlignmentTree> rights;

    private final VariableNumMap wordVar;
    private final VariableNumMap wordActiveVar;

    public AlignmentTree(VariableNumMap var, Expression expression, int numAppliedArguments,
        List<AlignmentTree> lefts, List<AlignmentTree> rights,
        VariableNumMap wordVar, VariableNumMap wordActiveVar) {
      this.var = Preconditions.checkNotNull(var);
      this.expression = Preconditions.checkNotNull(expression);
      
      this.numAppliedArguments = numAppliedArguments;

      this.lefts = Preconditions.checkNotNull(lefts);
      this.rights = Preconditions.checkNotNull(rights);
      this.wordVar = wordVar;
      this.wordActiveVar = wordActiveVar;
    }

    public VariableNumMap getVar() {
      return var;
    }

    public AlignedExpressionTree decodeAlignment(Assignment assignment, List<String> sentence) {
      Multimap<String, Integer> wordIndexes = HashMultimap.create();
      for (int i = 0; i < sentence.size(); i++) {
        wordIndexes.put(sentence.get(i), i);
      }
      return decodeAlignmentHelper(assignment, wordIndexes);
    }

    private AlignedExpressionTree decodeAlignmentHelper(Assignment assignment,
        Multimap<String, Integer> wordIndexes) {
      Preconditions.checkState(isActive(assignment));

      if (isWordActive(assignment)) {
        String word = (String) wordVar.assignmentToOutcome(assignment).get(0);
        
        // Get the spans in the sentence where this word appears 
        int[] spanStarts = Ints.toArray(wordIndexes.get(word));
        int[] spanEnds = Arrays.copyOf(spanStarts, spanStarts.length);
        for (int i = 0; i < spanEnds.length; i++) {
          spanEnds[i] += 1;
        }

        return AlignedExpressionTree.forTerminal(expression, numAppliedArguments,
            appliedArgumentSpec, spanStarts, spanEnds, word);
      } else {
        AlignedExpressionTree newLeft = null;
        AlignedExpressionTree newRight = null;
        for (int i = 0; i < lefts.size(); i++) {
          if (lefts.get(i).isActive(assignment)) {
            // Only a single child of a node should be active. 
            Preconditions.checkState(newLeft == null);
            newLeft = lefts.get(i).decodeAlignmentHelper(assignment, wordIndexes);
            newRight = rights.get(i).decodeAlignmentHelper(assignment, wordIndexes);
          }
        }
        Preconditions.checkState(newLeft != null);

        return AlignedExpressionTree.forNonterminal(expression, numAppliedArguments,
            appliedArgumentSpec, newLeft, newRight);
      }
    }
    
    /**
     * Returns {@code true} if this tree node is part of the
     * alignment given by {@code assignment}.
     * 
     * @param assignment
     * @return
     */
    public boolean isActive(Assignment assignment) {
      return var.assignmentToOutcome(assignment).equals(Arrays.asList("T"));
    }
    
    /**
     * Returns {@code true} if this tree node is part of the 
     * alignment given by {@code assignment} and if this node
     * is aligned to a word in the sentence (i.e., is a terminal).
     * 
     * @param assignment
     * @return
     */
    public boolean isWordActive(Assignment assignment) {
      return wordActiveVar.assignmentToOutcome(assignment).equals(Arrays.asList("T"));
    }
  }
}
