package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParseTree;
import com.jayantkrish.jklol.cfg.CfgParser;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;

public class CfgAlignmentModel {

  private final DiscreteFactor terminalFactor;

  private final VariableNumMap terminalVar;
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar;
  private final VariableNumMap parentVar;
  private final VariableNumMap ruleVar;

  private static final double EPSILON = 0.0001;
  
  public CfgAlignmentModel(DiscreteFactor terminalFactor, VariableNumMap terminalVar,
      VariableNumMap leftVar, VariableNumMap rightVar, VariableNumMap parentVar,
      VariableNumMap ruleVar) {
    this.terminalFactor = Preconditions.checkNotNull(terminalFactor);
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.leftVar = Preconditions.checkNotNull(leftVar);
    this.rightVar = Preconditions.checkNotNull(rightVar);
    this.parentVar = Preconditions.checkNotNull(parentVar);
    this.ruleVar = Preconditions.checkNotNull(ruleVar);
  }

  public AlignedExpressionTree getBestAlignment(AlignmentExample example) {
    CfgParser parser = getCfgParser(example);
    ExpressionTree tree = example.getTree();
    CfgParseChart chart = parser.parseMarginal(example.getWords(), tree.getExpression(), false);
    CfgParseTree parseTree = chart.getBestParseTree(tree.getExpression());
    
    return decodeCfgParse(parseTree, 0);
  }

  private AlignedExpressionTree decodeCfgParse(CfgParseTree t, int numAppliedArguments) {
    Preconditions.checkArgument(t.getRoot() != ParametricCfgAlignmentModel.SKIP_EXPRESSION);

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
      
      if (left == ParametricCfgAlignmentModel.SKIP_EXPRESSION) {
        return decodeCfgParse(t.getRight(), numAppliedArguments);
      } else if (right == ParametricCfgAlignmentModel.SKIP_EXPRESSION) {
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
        Preconditions.checkState(funcTree != null && argTree!= null); 
        
        AlignedExpressionTree leftTree = decodeCfgParse(argTree, 0);
        AlignedExpressionTree rightTree = decodeCfgParse(funcTree, numAppliedArguments + 1);

        return AlignedExpressionTree.forNonterminal(parent, numAppliedArguments,
            leftTree, rightTree);
      }
    }
  }

  public CfgParser getCfgParser(AlignmentExample example) {
    Set<Expression2> expressions = Sets.newHashSet();
    example.getTree().getAllExpressions(expressions);
    expressions.add(ParametricCfgAlignmentModel.SKIP_EXPRESSION);

    VariableNumMap binaryRuleVars = VariableNumMap.unionAll(leftVar, rightVar, parentVar, ruleVar);
    TableFactorBuilder binaryRuleBuilder = new TableFactorBuilder(binaryRuleVars,
        SparseTensorBuilder.getFactory());
    for (Expression2 e : expressions) {
      binaryRuleBuilder.setWeight(1.0, e, ParametricCfgAlignmentModel.SKIP_EXPRESSION,
          e, ParametricCfgAlignmentModel.SKIP_RULE);
      binaryRuleBuilder.setWeight(1.0, ParametricCfgAlignmentModel.SKIP_EXPRESSION, e,
          e, ParametricCfgAlignmentModel.SKIP_RULE);
    }
    
    populateBinaryRuleDistribution(example.getTree(), binaryRuleBuilder);
    TableFactor binaryDistribution = binaryRuleBuilder.build();

    return new CfgParser(parentVar, leftVar, rightVar, terminalVar, ruleVar,
        binaryDistribution, terminalFactor, -1, false);
  }
  
  private void populateBinaryRuleDistribution(ExpressionTree tree, TableFactorBuilder builder) {
    if (tree.hasChildren()) {
      Expression2 root = tree.getExpression();

      List<ExpressionTree> argChildren = tree.getLeftChildren();
      List<ExpressionTree> funcChildren = tree.getRightChildren();
      for (int i = 0; i < argChildren.size(); i++) {
        ExpressionTree arg = argChildren.get(i);
        ExpressionTree func = funcChildren.get(i);

        // Add binary rule for this combination of expressions. Note
        // that the expressions can occur in either order in the sentence.
        builder.setWeight(1 - EPSILON, arg.getExpression(),
            func.getExpression(), root, ParametricCfgAlignmentModel.BACKWARD_APPLICATION);
        builder.setWeight(1 - EPSILON, func.getExpression(),
            arg.getExpression(), root, ParametricCfgAlignmentModel.FORWARD_APPLICATION);
        
        // Populate children
        populateBinaryRuleDistribution(arg, builder);
        populateBinaryRuleDistribution(func, builder);
      }
    }
  }
}
