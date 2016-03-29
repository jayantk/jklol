package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree.ExpressionNode;

public class ExpressionTreeTest extends TestCase {
  
  String[] expressionStrings = new String[] {
      "(lambda ($0) (lambda ($1) (and:<t*,t> (state:<lo,t> $1) (next_to:<lo,<lo,t>> $0 $1))))",
      "(lambda ($0) (lambda ($1) (and:<t*,t> (state:<lo,t> $1) (next_to:<lo,<lo,t>> $0 texas:lo))))",
      "(lambda ($0) (and:<t*,t> (major:<lo,t> $0) (river:<lo,t> $0) (loc:<lo,<lo,t>> $0 texas:lo)))",
      "(count:<<e,t>,i> (lambda ($0) (and:<t*,t> (state:<s,t> $0) (exists:<<e,t>,t> (lambda ($1) (and:<t*,t> (city:<c,t> $1) (named:<e,<n,t>> $1 springfield:n) (loc:<lo,<lo,t>> $1 $0)))))))",
      "(lambda ($0) (cause:<e,<e,t>> (decrease:<a,e> \"raccoons\":a) ($0 \"fish\":a)))",
  };

  Expression2[] expressions = new Expression2[expressionStrings.length];
  
  ExpressionParser<Expression2> parser;
  ExpressionComparator comparator;
  
  public void setUp() {
    parser = ExpressionParser.expression2();
    comparator = new SimplificationComparator(ExpressionSimplifier.lambdaCalculus());
    for (int i = 0; i < expressionStrings.length; i++) {
      expressions[i] = parser.parse(expressionStrings[i]);
    }
  }

  public void testAnd() {
    ExpressionTree tree = ExpressionTree.fromExpression(expressions[0]);

    System.out.println(tree);
    
    assertTrue(tree.size() > 1);
  }

  public void testAnd2() {
    ExpressionTree tree = ExpressionTree.fromExpression(expressions[1]);

    System.out.println(tree);

    assertTrue(tree.size() > 1);
  }
  
  public void testAnd3() {
    ExpressionTree tree = ExpressionTree.fromExpression(expressions[3]);
    
    System.out.println(tree);
  }
  
  public void testExpressionTemplate() {
    TypeDeclaration t = ExplicitTypeDeclaration.getDefault();
    ExpressionTree tree = ExpressionTree.fromExpression(expressions[0]);
    System.out.println(tree.getExpressionNode());
    System.out.println(tree.getExpressionNode().getExpressionTemplate(t, 0));
    System.out.println(tree.getExpressionNode().getExpressionTemplate(t, 1));
    System.out.println(tree.getExpressionNode().getExpressionTemplate(t, 2));
  }
  
  public void testVacuousVariables() {
    ExpressionTree tree = ExpressionTree.fromExpression(expressions[4]);
    
    List<ExpressionNode> nodes = Lists.newArrayList();
    tree.getAllExpressionNodes(nodes);
    
    Expression2 vacuous = parser.parse("(lambda ($0) ($0 \"fish\":a))");
    
    for (ExpressionNode node : nodes) {
      assertFalse(comparator.equals(node.getExpression(), vacuous));
    }
  }
}
