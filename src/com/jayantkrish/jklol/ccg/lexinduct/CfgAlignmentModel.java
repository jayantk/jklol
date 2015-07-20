package com.jayantkrish.jklol.ccg.lexinduct;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
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

public class CfgAlignmentModel implements AlignmentModelInterface, Serializable {
  private static final long serialVersionUID = 1L;

  private final DiscreteFactor nonterminalFactor;
  private final DiscreteFactor terminalFactor;

  private final VariableNumMap terminalVar;
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar;
  private final VariableNumMap parentVar;
  private final VariableNumMap ruleVar;
  
  private final int nGramLength;

  private static final double EPSILON = 0.0001;
  
  public CfgAlignmentModel(DiscreteFactor nonterminalFactor, DiscreteFactor terminalFactor,
      VariableNumMap terminalVar, VariableNumMap leftVar, VariableNumMap rightVar, VariableNumMap parentVar,
      VariableNumMap ruleVar, int nGramLength) {
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

  public AlignedExpressionTree getBestAlignment(AlignmentExample example) {
    CfgParser parser = getCfgParser(example);
    ExpressionTree tree = example.getTree();
    
    Factor rootFactor = getRootFactor(tree, parser.getParentVariable());
    CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, false);
    CfgParseTree parseTree = chart.getBestParseTree();

    return decodeCfgParse(parseTree, 0);
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
      Expression2 expression = ((ExpressionNode) t.getRoot()).getExpression();
      return AlignedExpressionTree.forTerminal(expression,
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
        
        if (t.getRuleType().equals(ParametricCfgAlignmentModel.FORWARD_APPLICATION)) {
          // Thing on the left is the function
          funcTree = t.getLeft();
          argTree = t.getRight();
        } else if (t.getRuleType().equals(ParametricCfgAlignmentModel.BACKWARD_APPLICATION)) {
          // Thing on the right is the function
          funcTree = t.getRight();
          argTree = t.getLeft();
        }
        Preconditions.checkState(funcTree != null && argTree!= null, "Tree is broken %s", t); 
        
        AlignedExpressionTree leftTree = decodeCfgParse(argTree, 0);
        AlignedExpressionTree rightTree = decodeCfgParse(funcTree, numAppliedArguments + 1);

        return AlignedExpressionTree.forNonterminal(parent.getExpression(), numAppliedArguments,
            leftTree, rightTree);
      }
    }
  }

  public CfgParser getCfgParser(AlignmentExample example) {
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
    // DiscreteFactor binaryDistribution = binaryRuleBuilder.build().product(1.0 - EPSILON);
    DiscreteFactor binaryDistribution = binaryRuleBuilder.build();

    // Build a new terminal distribution over only these expressions.
    VariableNumMap newVars = VariableNumMap.unionAll(newTerminalVar, newParentVar, ruleVar);
    TableFactorBuilder newTerminalFactor = new TableFactorBuilder(newVars,
        DenseTensorBuilder.getFactory());
    for (List<String> word : words) {
      for (ExpressionNode expression : expressions) {
        Assignment a = newVars.outcomeArrayToAssignment(word, expression, ParametricCfgAlignmentModel.TERMINAL);
        double prob = terminalFactor.getUnnormalizedProbability(a);
        newTerminalFactor.setWeight(a, prob);
      }
    }

    return new CfgParser(newParentVar, newLeftVar, newRightVar, newTerminalVar, ruleVar,
        binaryDistribution, newTerminalFactor.build(), -1, false, null);
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
