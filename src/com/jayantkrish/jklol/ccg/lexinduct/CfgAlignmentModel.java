package com.jayantkrish.jklol.ccg.lexinduct;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree.ExpressionNode;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParseTree;
import com.jayantkrish.jklol.cfg.CfgParser;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class CfgAlignmentModel implements Serializable {
  private static final long serialVersionUID = 1L;

  private final DiscreteFactor nonterminalFactor;
  private final DiscreteFactor terminalFactor;

  private final VariableNumMap terminalVar;
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar;
  private final VariableNumMap parentVar;
  private final VariableNumMap ruleVar;
  
  private final int nGramLength;
  
  public CfgAlignmentModel(DiscreteFactor nonterminalFactor, DiscreteFactor terminalFactor,
      VariableNumMap terminalVar, VariableNumMap leftVar, VariableNumMap rightVar,
      VariableNumMap parentVar, VariableNumMap ruleVar, int nGramLength) {
    this.nonterminalFactor = Preconditions.checkNotNull(nonterminalFactor);
    Preconditions.checkArgument(nonterminalFactor.getVars().equals(
        VariableNumMap.unionAll(leftVar, rightVar, parentVar, ruleVar)));
    this.terminalFactor = Preconditions.checkNotNull(terminalFactor);
    Preconditions.checkArgument(terminalFactor.getVars().equals(
        VariableNumMap.unionAll(terminalVar, parentVar, ruleVar)));

    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.leftVar = Preconditions.checkNotNull(leftVar);
    this.rightVar = Preconditions.checkNotNull(rightVar);
    this.parentVar = Preconditions.checkNotNull(parentVar);
    this.ruleVar = Preconditions.checkNotNull(ruleVar);
    this.nGramLength = nGramLength;
  }

  public List<List<String>> getTerminalVarValues() {
    DiscreteVariable v = terminalVar.getDiscreteVariables().get(0);
    List<List<String>> values = Lists.newArrayList();
    for (Object o : v.getValues()) {
      List<String> value = Lists.newArrayList();
      for (Object obj : (List<?>) o) {
        value.add((String) obj);
      }
      values.add(value);
    }
    return values;
  }
  
  public void printStuffOut() {
    for (List<String> terminalVarValue : getTerminalVarValues()) {
      DiscreteFactor conditional = terminalFactor.conditional(terminalVar.outcomeArrayToAssignment(terminalVarValue));
      String description = conditional.describeAssignments(conditional.getMostLikelyAssignments(10));
      System.out.println(terminalVarValue);
      System.out.println(description);
    }
  }
  
  public VariableNumMap getParentVar() {
    return parentVar;
  }

  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }
  
  public DiscreteFactor getNonterminalFactor() {
    return nonterminalFactor;
  }
  
  public DiscreteFactor getTerminalFactor() {
    return terminalFactor;
  }

  public AlignedExpressionTree getBestAlignment(AlignmentExample example) {
    return getBestAlignment(example, TableFactor.logUnity(parentVar));
  }

  public AlignedExpressionTree getBestAlignment(AlignmentExample example, TableFactor expressionTerminalWeights) {
    CfgParser parser = getCfgParser(example, expressionTerminalWeights);
    ExpressionTree tree = example.getTree();
    
    Factor rootFactor = getRootFactor(tree, parser.getParentVariable());
    CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, false);
    CfgParseTree parseTree = chart.getBestParseTree();

    return decodeCfgParse(parseTree, 0);
  }

  public List<AlignedExpressionTree> getBestAlignments(AlignmentExample example, int beamSize) {
    TableFactor expressionTerminalWeights = TableFactor.logUnity(parentVar);
    CfgParser parser = getCfgParser(example, expressionTerminalWeights);
    ExpressionTree tree = example.getTree();
    
    Factor rootFactor = getRootFactor(tree, parser.getParentVariable());
    
    List<CfgParseTree> parseTrees = parser.beamSearch(example.getWords(), beamSize);
    List<AlignedExpressionTree> expressionTrees = Lists.newArrayList();
    for (CfgParseTree parseTree : parseTrees) {
      System.out.println(parseTree);
      if (rootFactor.getUnnormalizedProbability(parseTree.getRoot()) > 0) {
        expressionTrees.add(decodeCfgParse(parseTree, 0));
      }
    }
    return expressionTrees;
  }

  public AlignedExpressionTree decodeCfgParse(CfgParseTree t) {
    return decodeCfgParse(t, 0);
  }
  
  private AlignedExpressionTree decodeCfgParse(CfgParseTree t, int numAppliedArguments) {
    Preconditions.checkArgument(!t.getRoot().equals(ParametricCfgAlignmentModel.SKIP_EXPRESSION));

    if (t.isTerminal()) {
      // Expression tree spans have an exclusive end index.
      int[] spanStarts = new int[] {t.getSpanStart()};
      int[] spanEnds = new int[] {t.getSpanEnd() + 1};
      List<String> words = Lists.newArrayList();
      for (Object o : t.getTerminalProductions()) {
        words.add((String) o);
      }
      ExpressionNode root = ((ExpressionNode) t.getRoot());
      return AlignedExpressionTree.forTerminal(root.getExpression(), root.getType(), 
          numAppliedArguments, spanStarts, spanEnds, words);
    } else {
      ExpressionNode parent = ((ExpressionNode) t.getRoot());
      ExpressionNode left = ((ExpressionNode) t.getLeft().getRoot());
      ExpressionNode right = ((ExpressionNode) t.getRight().getRoot());

      if (left.equals(ParametricCfgAlignmentModel.SKIP_EXPRESSION)) {
        return decodeCfgParse(t.getRight(), numAppliedArguments);
      } else if (right.equals(ParametricCfgAlignmentModel.SKIP_EXPRESSION)) {
        return decodeCfgParse(t.getLeft(), numAppliedArguments);
      } else {
        // A combination of expressions.
        CfgParseTree argTree = null;
        CfgParseTree funcTree = null;

        // The argument's expression node must have 0 applied arguments
        // while the function's node must have > 0.
        if (left.getNumAppliedArguments() == 0) {
          // Thing on the right is the function
          funcTree = t.getRight();
          argTree = t.getLeft();
        } else if (right.getNumAppliedArguments() == 0) {
          // Thing on the left is the function
          funcTree = t.getLeft();
          argTree = t.getRight();
        }

        Preconditions.checkState(funcTree != null && argTree!= null, "Tree is broken %s", t); 
        
        AlignedExpressionTree leftTree = decodeCfgParse(argTree, 0);
        AlignedExpressionTree rightTree = decodeCfgParse(funcTree, numAppliedArguments + 1);

        return AlignedExpressionTree.forNonterminal(parent.getExpression(), parent.getType(), 
            numAppliedArguments, leftTree, rightTree);
      }
    }
  }

  public CfgParser getCfgParser(AlignmentExample example) {
    return getCfgParser(example, nonterminalFactor, terminalFactor, TableFactor.logUnity(parentVar.union(terminalVar)));
  }
  
  public CfgParser getCfgParser(AlignmentExample example, DiscreteFactor expressionTerminalWeights) {
    return getCfgParser(example, nonterminalFactor, terminalFactor, expressionTerminalWeights);
  }
  
  public CfgParser getUniformCfgParser(AlignmentExample example) {
    return getCfgParser(example, TableFactor.logUnity(nonterminalFactor.getVars()), TableFactor.logUnity(terminalFactor.getVars()),
        TableFactor.logUnity(parentVar.union(terminalVar)));
  }

  private CfgParser getCfgParser(AlignmentExample example, DiscreteFactor nonterminalFactor, DiscreteFactor terminalFactor,
      DiscreteFactor expressionTerminalWeights) {
    Set<ExpressionNode> expressions = Sets.newHashSet();
    example.getTree().getAllExpressionNodes(expressions);
    expressions.add(ParametricCfgAlignmentModel.SKIP_EXPRESSION);

    Set<List<String>> words = Sets.newHashSet();
    words.addAll(example.getNGrams(nGramLength));
    words.retainAll(terminalVar.getDiscreteVariables().get(0).getValues());

    // Build a new CFG parser restricted to these logical forms:
    DiscreteVariable expressionVar = new DiscreteVariable("new-expressions", expressions);
    DiscreteVariable wordVar = new DiscreteVariable("new-words", words);
    VariableNumMap newLeftVar = VariableNumMap.singleton(leftVar.getOnlyVariableNum(), leftVar.getOnlyVariableName(), expressionVar);
    VariableNumMap newRightVar = VariableNumMap.singleton(rightVar.getOnlyVariableNum(), rightVar.getOnlyVariableName(), expressionVar);
    VariableNumMap newParentVar = VariableNumMap.singleton(parentVar.getOnlyVariableNum(), parentVar.getOnlyVariableName(), expressionVar);
    VariableNumMap newTerminalVar = VariableNumMap.singleton(terminalVar.getOnlyVariableNum(), terminalVar.getOnlyVariableName(), wordVar);

    VariableNumMap binaryRuleVars = VariableNumMap.unionAll(newLeftVar, newRightVar, newParentVar, ruleVar);
    TableFactorBuilder binaryRuleBuilder = new TableFactorBuilder(binaryRuleVars,
        SparseTensorBuilder.getFactory());
    example.getTree().populateBinaryRuleDistribution(binaryRuleBuilder, nonterminalFactor);
    DiscreteFactor binaryDistribution = binaryRuleBuilder.build();

    // Build a new terminal distribution over only these expressions.
    VariableNumMap newVars = VariableNumMap.unionAll(newTerminalVar, newParentVar, ruleVar);
    TableFactorBuilder newTerminalFactor = new TableFactorBuilder(newVars,
        DenseTensorBuilder.getFactory());

    populateTerminalDistribution(example.getWords(), expressions, terminalFactor, expressionTerminalWeights,
        newTerminalFactor);

    // No special treatment of the skip symbol:
    return new CfgParser(newParentVar, newLeftVar, newRightVar, newTerminalVar, ruleVar,
        binaryDistribution, newTerminalFactor.build(), false, null);

    // Parser with special skip assignment:
    /*
    return new CfgParser(newParentVar, newLeftVar, newRightVar, newTerminalVar, ruleVar,
        binaryDistribution, newTerminalFactor.build(), true,
        newParentVar.outcomeArrayToAssignment(ParametricCfgAlignmentModel.SKIP_EXPRESSION)
        .union(ruleVar.outcomeArrayToAssignment(ParametricCfgAlignmentModel.TERMINAL)));
        */
  }
  
  public void populateTerminalDistribution(List<String> exampleWords, Collection<?> expressions,
      DiscreteFactor terminalFactor, DiscreteFactor expressionTerminalWeights, TableFactorBuilder builder) {
    VariableNumMap newVars = builder.getVars();
    for (Object expression : expressions) {
      for (int i = 0; i < exampleWords.size(); i++) {
        double prob = 1.0;

        for (int j = i; j < Math.min(i + nGramLength, exampleWords.size()); j++) {
          Assignment a = terminalFactor.getVars().outcomeArrayToAssignment(exampleWords.subList(i, j + 1),
              expression, ParametricCfgAlignmentModel.TERMINAL);
          if (terminalFactor.getVars().isValidAssignment(a)) {
            double entryProb = prob * terminalFactor.getUnnormalizedProbability(a);
            entryProb *= expressionTerminalWeights.getUnnormalizedProbability(a.intersection(expressionTerminalWeights.getVars()));

            Assignment terminalAssignment = newVars.outcomeArrayToAssignment(exampleWords.subList(i, j + 1),
                expression, ParametricCfgAlignmentModel.TERMINAL);

            builder.setWeight(terminalAssignment, entryProb);
          }
        }
      }
    }
  }

  /**
   * This method is a hack that enables the use of the "substitutions"
   * field of ExpressionTree at the root of the CFG parse. In the future,
   * these substitutions should be handled using unary rules in the 
   * CFG parser.
   */
  public Factor getRootFactor(ExpressionTree tree, VariableNumMap expressionVar) {
    List<Assignment> roots = Lists.newArrayList();
    if (tree.getSubstitutions().size() == 0) {
      roots.add(expressionVar.outcomeArrayToAssignment(tree.getExpressionNode()));
    } else {
      for (ExpressionTree substitution : tree.getSubstitutions()) {
        roots.add(expressionVar.outcomeArrayToAssignment(substitution.getExpressionNode()));
      }
    }

    return TableFactor.pointDistribution(expressionVar, roots.toArray(new Assignment[0]));
  }
}
