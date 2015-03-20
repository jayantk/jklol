package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

public class ExpressionTreeTest extends TestCase {
  String exp1 = "(lambda $f0 (lambda $f1 (and:<t*,t> (state:<s,t> $f1) (next_to:<lo,<lo,t>> $f0 $f1))))";
  
  Set<ConstantExpression> disallowedConstants = Sets.newHashSet(new ConstantExpression("and:<t*,t>"));
  
  public void testAnd() {
    Expression exp = ExpressionParser.lambdaCalculus().parseSingleExpression(exp1);
    ExpressionTree tree = ExpressionTree.fromExpression(exp, disallowedConstants);

    System.out.println(tree);
    
    assertTrue(tree.size() > 1);
  }
}
