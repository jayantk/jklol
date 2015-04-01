package com.jayantkrish.jklol.ccg.lexinduct;

import java.io.Serializable;
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
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParseTree;
import com.jayantkrish.jklol.cfg.CfgParser;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.Factors;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraphBuilder;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariableNumPattern;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;

public class AlignmentModel implements Serializable {
  private static final long serialVersionUID = 1L;

  private final DynamicFactorGraph factorGraph;
  
  private final String expressionPlateName;

  private final VariableNumMap expressionPlateVar;
  private final VariableNumMap featurePlateVar;
  private final VariableNumMap wordPlateVar;
  
  private final String booleanPlateName;
  private final VariableNumMap booleanPlateVar;
  
  private final FeatureVectorGenerator<Expression2> featureGen;
  
  private final boolean useTreeConstraint;

  private final VariableNumMap wordActiveWord, wordActiveBoolean;
  private final Factor wordActiveFactor;
  
  private final VariableNumMap input1, input2, output;
  private final Factor andFactor;
  private final Factor orFactor;
  
  // The probability of splitting an expression is set to
  // 1 - epsilon to favor larger expressions when probabilities
  // are otherwise tied.
  private static final double EPSILON = 0.0001;

  // Names for CFG rules used in the CFG decoding algorithm.
  private static final String TERMINAL = "terminal";
  private static final String FORWARD_APPLICATION = "fa";
  private static final String BACKWARD_APPLICATION = "ba";
  private static final String SKIP_RULE = "skip";
  private static final Expression2 SKIP_EXPRESSION = Expression2.constant("**skip**");

  public AlignmentModel(DynamicFactorGraph factorGraph, String expressionPlateName,
      VariableNumMap expressionPlateVar, VariableNumMap featurePlateVar,
      VariableNumMap wordPlateVar, String booleanPlateName,
      VariableNumMap booleanPlateVar, FeatureVectorGenerator<Expression2> featureGen,
      boolean useTreeConstraint) {
    this.factorGraph = Preconditions.checkNotNull(factorGraph);

    this.expressionPlateName = Preconditions.checkNotNull(expressionPlateName);
    this.expressionPlateVar = Preconditions.checkNotNull(expressionPlateVar);
    this.featurePlateVar = Preconditions.checkNotNull(featurePlateVar);
    this.wordPlateVar = Preconditions.checkNotNull(wordPlateVar);

    this.booleanPlateName = booleanPlateName;
    this.booleanPlateVar = booleanPlateVar;
    
    this.featureGen = featureGen;

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
        bools.outcomeArrayToAssignment("T", "T", "T")).product(1 - EPSILON));

    this.orFactor = TableFactor.pointDistribution(bools, 
        bools.outcomeArrayToAssignment("F", "F", "F"),
        bools.outcomeArrayToAssignment("T", "F", "T"),
        bools.outcomeArrayToAssignment("F", "T", "T"));
  }

  public AlignedExpressionTree getBestAlignment(AlignmentExample example) {
    if (example.getTree().getExpressionFeatures() == null) {
      example = new AlignmentExample(example.getWords(),
          example.getTree().applyFeatureVectorGenerator(featureGen));
    }
    
    Pair<FactorGraph, AlignmentTree> pair = getFactorGraphWithTreeConstraint(example);
    FactorGraph fg = pair.getLeft();
    AlignmentTree tree = pair.getRight();
    JunctionTree jt = new JunctionTree(true);
    MaxMarginalSet maxMarginals = jt.computeMaxMarginals(fg);

    Assignment best = maxMarginals.getNthBestAssignment(0);

    return tree.decodeAlignment(best, example.getWords());
  }

  public AlignedExpressionTree getBestAlignmentCfg(AlignmentExample example) {
    CfgParser parser = getCfgParser(example);
    ExpressionTree tree = example.getTree();
    CfgParseChart chart = parser.parseMarginal(example.getWords(), tree.getExpression(), false);
    CfgParseTree parseTree = chart.getBestParseTree(tree.getExpression());
    
    System.out.println(parseTree);
    return decodeCfgParse(parseTree, 0);
  }
  
  private AlignedExpressionTree decodeCfgParse(CfgParseTree t, int numAppliedArguments) {
    Preconditions.checkArgument(t.getRoot() != SKIP_EXPRESSION);

    if (t.isTerminal()) {
      // Expression tree spans have an exclusive end index.
      int[] spanStarts = new int[] {t.getSpanStart()};
      int[] spanEnds = new int[] {t.getSpanEnd() + 1};
      String word = (String) t.getTerminalProductions().get(0);
      return AlignedExpressionTree.forTerminal((Expression2) t.getRoot(),
          numAppliedArguments, spanStarts, spanEnds, word);
    } else {
      Expression2 parent = (Expression2) t.getRoot();
      Expression2 left = (Expression2) t.getLeft().getRoot();
      Expression2 right = (Expression2) t.getRight().getRoot();
      
      if (left == SKIP_EXPRESSION) {
        return decodeCfgParse(t.getRight(), numAppliedArguments);
      } else if (right == SKIP_EXPRESSION) {
        return decodeCfgParse(t.getLeft(), numAppliedArguments);
      } else {
        // A combination of expressions.
        CfgParseTree argTree = null;
        CfgParseTree funcTree = null;
        
        if (t.getRuleType().equals(FORWARD_APPLICATION)) {
          // Thing on the left is the function
          funcTree = t.getLeft();
          argTree = t.getRight();
        } else if (t.getRuleType().equals(BACKWARD_APPLICATION)) {
          // Thing on the right is the function
          funcTree = t.getRight();
          argTree = t.getLeft();
        }
        Preconditions.checkState(funcTree != null && argTree!= null); 
        
        AlignedExpressionTree leftTree = decodeCfgParse(argTree, 0);
        AlignedExpressionTree rightTree = decodeCfgParse(funcTree, numAppliedArguments + 1);

        return AlignedExpressionTree.forNonterminal(parent, numAppliedArguments,
            leftTree, rightTree);
      }
    }
  }
  
  public CfgParser getCfgParser(AlignmentExample example) {
    if (example.getTree().getExpressionFeatures() == null) {
      example = new AlignmentExample(example.getWords(),
          example.getTree().applyFeatureVectorGenerator(featureGen));
    }

    Pair<FactorGraph, AlignmentTree> pair = getFactorGraphWithTreeConstraint(example);
    FactorGraph fg = pair.getLeft();
    AlignmentTree tree = pair.getRight();
    
    Set<Expression2> expressions = Sets.newHashSet();
    example.getTree().getAllExpressions(expressions);
    expressions.add(SKIP_EXPRESSION);

    List<List<String>> terminalVarValues = Lists.newArrayList();
    for (String word : example.getWords()) {
      terminalVarValues.add(Arrays.asList(word));
    }

    DiscreteVariable expressionVarType = new DiscreteVariable("expressions", expressions);
    DiscreteVariable terminalVarType = new DiscreteVariable("words", terminalVarValues);
    DiscreteVariable ruleVarType = new DiscreteVariable("rule", Arrays.asList(TERMINAL,
        FORWARD_APPLICATION, BACKWARD_APPLICATION, SKIP_RULE));
    
    VariableNumMap terminalVar = VariableNumMap.singleton(0, "terminal", terminalVarType);
    VariableNumMap leftVar = VariableNumMap.singleton(1, "left", expressionVarType);
    VariableNumMap rightVar = VariableNumMap.singleton(2, "right", expressionVarType);
    VariableNumMap parentVar = VariableNumMap.singleton(3, "parent", expressionVarType);
    VariableNumMap ruleVar = VariableNumMap.singleton(4, "rule", ruleVarType);
    
    VariableNumMap binaryRuleVars = VariableNumMap.unionAll(leftVar, rightVar, parentVar, ruleVar);
    TableFactorBuilder binaryRuleBuilder = new TableFactorBuilder(binaryRuleVars,
        SparseTensorBuilder.getFactory());
    for (Expression2 e : expressions) {
      binaryRuleBuilder.setWeight(1.0, e, SKIP_EXPRESSION, e, SKIP_RULE);
      binaryRuleBuilder.setWeight(1.0, SKIP_EXPRESSION, e, e, SKIP_RULE);
    }
    
    VariableNumMap terminalRuleVars = VariableNumMap.unionAll(terminalVar, parentVar, ruleVar);
    TableFactorBuilder terminalRuleBuilder = new TableFactorBuilder(terminalRuleVars,
        DenseTensorBuilder.getFactory());
    for (List<String> terminalVarValue : terminalVarValues) {
      terminalRuleBuilder.setWeight(1.0, terminalVarValue, SKIP_EXPRESSION, SKIP_RULE);
    }

    tree.populateCfgDistributions(binaryRuleBuilder, terminalRuleBuilder, fg);
    
    TableFactor binaryDistribution = binaryRuleBuilder.build();
    TableFactor terminalDistribution = terminalRuleBuilder.build();
    
    return new CfgParser(parentVar, leftVar, rightVar, terminalVar, ruleVar,
        binaryDistribution, terminalDistribution, -1, false);
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
    List<Expression2> treeExpressions = Lists.newArrayList();
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
    private final Expression2 expression;

    // Number of arguments of expression that get
    // applied in this tree.
    private final int numAppliedArguments;
    
    private final List<AlignmentTree> lefts;
    private final List<AlignmentTree> rights;

    private final VariableNumMap wordVar;
    private final VariableNumMap wordActiveVar;

    public AlignmentTree(VariableNumMap var, Expression2 expression, int numAppliedArguments,
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

    public Expression2 getExpression() {
      return expression;
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
            spanStarts, spanEnds, word);
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
            newLeft, newRight);
      }
    }
    
    public void populateCfgDistributions(TableFactorBuilder nonterminals,
        TableFactorBuilder terminals, FactorGraph fg) {
      
      // Copy weights from the distribution P(l | w) over
      // to the terminal distribution of the CFG.
      List<Factor> wordFactors = Lists.newArrayList();
      for (int factorNum : fg.getFactorsWithVariable(wordVar.getOnlyVariableNum())) {
        Factor f = fg.getFactor(factorNum);
        if (!f.getVars().containsAny(wordActiveVar.getVariableNumsArray())) {
          wordFactors.add(f);
        }
      }
      DiscreteFactor wordFactor = Factors.product(wordFactors).coerceToDiscrete();
      VariableNumMap nonWordVars = wordFactor.getVars().removeAll(wordVar);
      wordFactor = wordFactor.marginalize(nonWordVars);

      for (Assignment a : wordFactor.getNonzeroAssignments()) {
        Object value = a.getOnlyValue();
        if (!value.equals(ParametricAlignmentModel.NULL_WORD)) {
          // Null word is used to handle unaligned expressions that are
          // inactive. These are not a problem in the CFG.
          double prob = wordFactor.getUnnormalizedProbability(a);
          terminals.setWeight(prob, Arrays.asList(value), expression, TERMINAL);
        }
      }
      
      
      for (int i = 0 ; i < lefts.size(); i++) {
        lefts.get(i).populateCfgDistributions(nonterminals, terminals, fg);
        rights.get(i).populateCfgDistributions(nonterminals, terminals, fg);

        // Add binary rule for this combination of expressions. Note
        // that the expressions can occur in either order in the sentence.
        nonterminals.setWeight(1 - EPSILON, lefts.get(i).expression,
            rights.get(i).expression, expression, BACKWARD_APPLICATION);
        nonterminals.setWeight(1 - EPSILON, rights.get(i).expression,
            lefts.get(i).expression, expression, FORWARD_APPLICATION);
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
