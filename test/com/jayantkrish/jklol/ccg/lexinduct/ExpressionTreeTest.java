package com.jayantkrish.jklol.ccg.lexinduct;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

public class ExpressionTreeTest extends TestCase {
  
  String[] expressionStrings = new String[] {
      "(lambda $0 (lambda $1 (and:<t*,t> (state:<s,t> $1) (next_to:<lo,<lo,t>> $0 $1))))",
      "(lambda $0 (lambda $1 (and:<t*,t> (state:<s,t> $1) (next_to:<lo,<lo,t>> $0 texas:s))))",
      "(lambda $0 (and:<t*,t> (major:<lo,t> $0) (river:<r,t> $0) (loc:<lo,<lo,t>> $0 texas:s)))",
  };
  
  Expression2[] expressions = new Expression2[expressionStrings.length];
  
  public void setUp() {
    ExpressionParser<Expression2> parser = ExpressionParser.expression2();
    for (int i = 0; i < expressionStrings.length; i++) {
      expressions[i] = parser.parseSingleExpression(expressionStrings[i]);
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
}
