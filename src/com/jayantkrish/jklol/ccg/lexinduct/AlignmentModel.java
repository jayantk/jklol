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
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
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
    
    // TODO: configuration of the scaling parameter on the ands.
    VariableNumMap bools = VariableNumMap.unionAll(input1, input2, output);
    this.andFactor = TableFactor.pointDistribution(bools, 
        bools.outcomeArrayToAssignment("F", "F", "F")).add(TableFactor.pointDistribution(bools, 
        bools.outcomeArrayToAssignment("T", "T", "T")).product(0.9999));

    this.orFactor = TableFactor.pointDistribution(bools, 
        bools.outcomeArrayToAssignment("F", "F", "F"),
        bools.outcomeArrayToAssignment("T", "F", "T"),
        bools.outcomeArrayToAssignment("F", "T", "T"));
  }

  public AlignmentTree getBestAlignment(AlignmentExample example) {
    Pair<FactorGraph, AlignmentTree> pair = getFactorGraphWithTreeConstraint(example);
    FactorGraph fg = pair.getLeft();
    AlignmentTree tree = pair.getRight();
    JunctionTree jt = new JunctionTree(true);
    MaxMarginalSet maxMarginals = jt.computeMaxMarginals(fg);

    Assignment best = maxMarginals.getNthBestAssignment(0);

    AlignmentTree pruned = tree.pruneWithAssignment(best)
        .alignTreeToSentenceSpans(example.getWords()).generateCcgCategories();

    return pruned;
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
      return new AlignmentTree(wordActiveVar, tree.getExpression(), tree.getNumAppliedArguments(), null,
          new int[0], new int[0], Collections.<AlignmentTree>emptyList(), Collections.<AlignmentTree>emptyList(),
          wordVar, wordActiveVar, null);
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

      return new AlignmentTree(orVar, tree.getExpression(), tree.getNumAppliedArguments(), null,
          new int[0], new int[0], newLefts, newRights, wordVar, wordActiveVar, null);
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
  
  public static class AlignmentTree {
    private final VariableNumMap var;
    private final Expression expression;

    // Number of arguments of expression that get
    // applied in this tree.
    private final int numAppliedArguments;
    // Type specification (number of arguments) of each
    // argument that this function is applied to.
    private final int[] appliedArgumentSpec;
    
    // Possible spans of the input sentence that this
    // node of the tree could be aligned to.
    private final int[] possibleSpanStarts;
    private final int[] possibleSpanEnds;
    
    private final List<AlignmentTree> lefts;
    private final List<AlignmentTree> rights;

    private final VariableNumMap wordVar;
    private final VariableNumMap wordActiveVar;
    private final String word;

    public AlignmentTree(VariableNumMap var, Expression expression, int numAppliedArguments,
        int[] appliedArgumentSpec, int[] possibleSpanStarts, int[] possibleSpanEnds,
        List<AlignmentTree> lefts, List<AlignmentTree> rights,
        VariableNumMap wordVar, VariableNumMap wordActiveVar, String word) {
      this.var = Preconditions.checkNotNull(var);
      this.expression = Preconditions.checkNotNull(expression);
      
      this.numAppliedArguments = numAppliedArguments;
      this.appliedArgumentSpec = appliedArgumentSpec;
      this.possibleSpanStarts = possibleSpanStarts;
      this.possibleSpanEnds = possibleSpanEnds;

      this.lefts = Preconditions.checkNotNull(lefts);
      this.rights = Preconditions.checkNotNull(rights);
      this.wordVar = wordVar;
      this.wordActiveVar = wordActiveVar;
      this.word = word;
    }

    public VariableNumMap getVar() {
      return var;
    }
    public VariableNumMap getWordActiveVar() {
      return wordActiveVar;
    }
    public Expression getExpression() {
      return expression;
    }
    public List<AlignmentTree> getLefts() {
      return lefts;
    }
    public List<AlignmentTree> getRights() {
      return rights;
    }
    public int[] getSpanStarts() {
      return possibleSpanStarts;
    }
    public int[] getSpanEnds() {
      return possibleSpanEnds;
    }

    public Multimap<String, AlignedExpression> getWordAlignments() {
      Multimap<String, AlignedExpression> alignments = HashMultimap.create();
      getWordAlignmentsHelper(alignments);
      return alignments;
    }

    private void getWordAlignmentsHelper(Multimap<String, AlignedExpression> map) {
      if (word != null && !word.equals(ParametricAlignmentModel.NULL_WORD)) {
        map.put(word, new AlignedExpression(word, expression, numAppliedArguments,
            appliedArgumentSpec));
      }
      
      for (int i = 0; i < lefts.size(); i++) {
        lefts.get(i).getWordAlignmentsHelper(map);
        rights.get(i).getWordAlignmentsHelper(map);
      }
    }

    public AlignmentTree generateCcgCategories() {
      return generateCcgCategoriesHelper(Collections.<AlignmentTree>emptyList());
    }
    
    private AlignmentTree generateCcgCategoriesHelper(List<AlignmentTree> argumentStack) {
      if (lefts.size() == 0) {
        int[] argumentTypeSpec = new int[argumentStack.size()];
        for (int i = 0; i < argumentStack.size(); i++) {
          int numUnboundArgs = 0;
          Expression arg = argumentStack.get(i).getExpression();
          if (arg instanceof LambdaExpression) {
            numUnboundArgs = ((LambdaExpression) arg).getArguments().size();
          }
          argumentTypeSpec[argumentStack.size() - (1 + i)] = numUnboundArgs;
        }

        return new AlignmentTree(var, expression, numAppliedArguments, argumentTypeSpec,
            possibleSpanStarts, possibleSpanEnds, lefts, rights, wordVar, wordActiveVar, word);
        
      } else {
        Preconditions.checkArgument(lefts.size() == 1);
        AlignmentTree left = lefts.get(0);
        AlignmentTree right = rights.get(0);
        
        AlignmentTree newLeft = left.generateCcgCategoriesHelper(Collections.<AlignmentTree>emptyList());

        List<AlignmentTree> newArgs = Lists.newArrayList(argumentStack);
        newArgs.add(newLeft);        
        AlignmentTree newRight = right.generateCcgCategoriesHelper(newArgs);
        
        return new AlignmentTree(var, expression, numAppliedArguments, null,
            possibleSpanStarts, possibleSpanEnds, Arrays.asList(newLeft),
            Arrays.asList(newRight), wordVar, wordActiveVar, word);
      }
    }

    /*
    private void generateCcgCategoriesHelper(List<AlignmentTree> argumentStack) {
      if (lefts.size() == 0) {
        
        SyntacticCategory syntax = SyntacticCategory.parseFrom("N");
        for (int i = argumentStack.size() - 1; i >= 0; i--) {
          AlignmentTree arg = argumentStack.get(i);
          boolean left = false;
          boolean right = false;
          
          int[] argSpanStarts = arg.getSpanStarts();
          int[] argSpanEnds = arg.getSpanEnds();
          for (int j = 0; j < argSpanStarts.length; j++) {
            for (int k = 0; k < possibleSpanStarts.length; k++) {
              if (argSpanEnds[j] <= possibleSpanStarts[k]) {
                left = true;
              }
              if (possibleSpanEnds[k] <= argSpanStarts[j]) {
                right = true;
              }
            }
          }

          if (left) {
            syntax = syntax.addArgument(SyntacticCategory.parseFrom("N"), Direction.RIGHT);
          } else if (right) {
            syntax = syntax.addArgument(SyntacticCategory.parseFrom("N"), Direction.LEFT);
          } else {
            System.out.println("Problem.");
          }
        }
        System.out.println(syntax + " " + expression);
        
      } else {
        Preconditions.checkArgument(lefts.size() == 1);
        AlignmentTree left = lefts.get(0);
        AlignmentTree right = rights.get(0);
        
        List<AlignmentTree> newArgs = Lists.newArrayList(argumentStack);
        newArgs.add(left);
        left.generateCcgCategoriesHelper(Collections.<AlignmentTree>emptyList());
        right.generateCcgCategoriesHelper(newArgs);
      }
    }
    */
    
    public AlignmentTree alignTreeToSentenceSpans(List<String> sentence) {
      Multimap<String, Integer> wordIndexes = HashMultimap.create();
      for (int i = 0; i < sentence.size(); i++) {
        wordIndexes.put(sentence.get(i), i);
      }
      return alignTreeToSentenceSpansHelper(wordIndexes);
    }
    
    private AlignmentTree alignTreeToSentenceSpansHelper(Multimap<String, Integer> wordIndexes) {
      if (lefts.size() == 0) {
        // Terminal nodes align to individual words.
        int[] spanStarts = Ints.toArray(wordIndexes.get(word));
        int[] spanEnds = Arrays.copyOf(spanStarts, spanStarts.length);
        for (int i = 0; i < spanEnds.length; i++) {
          spanEnds[i] += 1;
        }

        return new AlignmentTree(var, expression, numAppliedArguments, appliedArgumentSpec,
            spanStarts, spanEnds, lefts, rights, wordVar, wordActiveVar, word);
      } else {
        // This method assumes that the tree has already been pruned.
        Preconditions.checkArgument(lefts.size() == 1);
        AlignmentTree left = lefts.get(0).alignTreeToSentenceSpansHelper(wordIndexes);
        AlignmentTree right = rights.get(0).alignTreeToSentenceSpansHelper(wordIndexes);
        
        List<Integer> spanStarts = Lists.newArrayList();
        List<Integer> spanEnds = Lists.newArrayList();
        for (int i = 0; i < left.possibleSpanStarts.length; i++) {
          for (int j = 0; j < right.possibleSpanStarts.length; j++) {
            int leftSpanStart = left.possibleSpanStarts[i];
            int leftSpanEnd = left.possibleSpanEnds[i];
            int rightSpanStart = right.possibleSpanStarts[j];
            int rightSpanEnd = right.possibleSpanEnds[j];

            // Spans can compose as long as they do not overlap.
            if (leftSpanEnd <= rightSpanStart || rightSpanEnd <= leftSpanStart) {
              spanStarts.add(Math.min(leftSpanStart, rightSpanStart));
              spanEnds.add(Math.max(leftSpanEnd, rightSpanEnd));
            }
          }
        }

        return new AlignmentTree(var, expression, numAppliedArguments, appliedArgumentSpec, Ints.toArray(spanStarts),
            Ints.toArray(spanEnds), Arrays.asList(left), Arrays.asList(right), wordVar, wordActiveVar, word);
      }
    }

    public AlignmentTree pruneWithAssignment(Assignment assignment) {
      if (var.assignmentToOutcome(assignment).equals(Arrays.asList("T"))) {

        List<AlignmentTree> newLefts = Lists.newArrayList();
        List<AlignmentTree> newRights = Lists.newArrayList();
        for (int i = 0; i < lefts.size(); i++) {
          AlignmentTree left = lefts.get(i).pruneWithAssignment(assignment);
          AlignmentTree right = rights.get(i).pruneWithAssignment(assignment);

          Preconditions.checkState((left == null && right == null) ||
              (left != null && right != null));
          
          if (left != null) {
            newLefts.add(left);
            newRights.add(right);
          }
        }
        String word = (String) wordVar.assignmentToOutcome(assignment).get(0);
        return new AlignmentTree(var, expression, numAppliedArguments, appliedArgumentSpec, possibleSpanStarts,
            possibleSpanEnds, newLefts, newRights, wordVar, wordActiveVar, word);
      } else {
        return null;
      }
    }
    
    public String toString() {
      StringBuilder sb = new StringBuilder();
      toStringHelper(this, sb, 0);
      return sb.toString();
    }

    private static void toStringHelper(AlignmentTree tree, StringBuilder sb, int depth) {
      for (int i = 0 ; i < depth; i++) {
        sb.append(" ");
      }
      sb.append(tree.expression);
      sb.append(" ");
      sb.append(tree.numAppliedArguments);
      sb.append(" ");
      for (int i = 0; i < tree.possibleSpanStarts.length; i++) {
        sb.append("[");
        sb.append(tree.possibleSpanStarts[i]);
        sb.append(",");
        sb.append(tree.possibleSpanEnds[i]);
        sb.append("]");
      }
      
      if (tree.word != ParametricAlignmentModel.NULL_WORD) {
        sb.append(" -> \"");
        sb.append(tree.word);
        sb.append("\"");
      }
      sb.append("\n");

      for (int i = 0; i < tree.lefts.size(); i++) {
        toStringHelper(tree.lefts.get(i), sb, depth + 2);      
        toStringHelper(tree.rights.get(i), sb, depth + 2);
      }
    }
  }
  
  public static class AlignedExpression {
    private final String word;
    private final Expression expression;
    private final int numAppliedArgs;
    private final int[] argTypes;

    public AlignedExpression(String word, Expression expression, int numAppliedArgs,
        int[] argTypes) {
      this.word = Preconditions.checkNotNull(word);
      this.expression = Preconditions.checkNotNull(expression);
      this.numAppliedArgs = numAppliedArgs;
      this.argTypes = argTypes;
    }

    public String getWord() {
      return word;
    }

    public Expression getExpression() {
      return expression;
    }

    public int getNumAppliedArgs() {
      return numAppliedArgs;
    }

    public int[] getArgTypes() {
      return argTypes;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((expression == null) ? 0 : expression.hashCode());
      result = prime * result + numAppliedArgs;
      result = prime * result + ((word == null) ? 0 : word.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      AlignedExpression other = (AlignedExpression) obj;
      if (expression == null) {
        if (other.expression != null)
          return false;
      } else if (!expression.equals(other.expression))
        return false;
      if (numAppliedArgs != other.numAppliedArgs)
        return false;
      if (word == null) {
        if (other.word != null)
          return false;
      } else if (!word.equals(other.word))
        return false;
      return true;
    }
  }
}
