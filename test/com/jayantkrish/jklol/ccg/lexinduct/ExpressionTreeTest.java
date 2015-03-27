package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

public class ExpressionTreeTest extends TestCase {
  String exp1 = "(lambda $0 (lambda $1 (and:<t*,t> (state:<s,t> $1) (next_to:<lo,<lo,t>> $0 $1))))";
  String exp2 = "(lambda $0 (lambda $1 (and:<t*,t> (state:<s,t> $1) (next_to:<lo,<lo,t>> $0 texas:s))))";
  String exp3 = "(lambda $0 (and:<t*,t> (major:<lo,t> $0) (river:<r,t> $0) (loc:<lo,<lo,t>> $0 texas:s)))";
  
  Set<ConstantExpression> disallowedConstants = Sets.newHashSet(new ConstantExpression("and:<t*,t>"));
  
  public void testAnd() {
    Expression exp = ExpressionParser.lambdaCalculus().parseSingleExpression(exp1);
    ExpressionTree tree = ExpressionTree.fromExpression(exp, disallowedConstants);

    System.out.println(tree);
    
    assertTrue(tree.size() > 1);
  }

  public void testAnd2() {
    Expression exp = ExpressionParser.lambdaCalculus().parseSingleExpression(exp2);
    ExpressionTree tree = ExpressionTree.fromExpression(exp, disallowedConstants);

    System.out.println(tree);

    assertTrue(tree.size() > 1);
  }

  public void testAnd3() {
    // TODO: handle adding conjuncts in ANDs, e.g., for "major"
    Expression exp = ExpressionParser.lambdaCalculus().parseSingleExpression(exp3);
    ExpressionTree tree = ExpressionTree.fromExpression(exp, disallowedConstants);

    System.out.println(tree);

    assertTrue(false);
  }
}
