package com.jayantkrish.jklol.ccg.lambda2;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

public class CpsTransformTest extends TestCase {

  public void testConstant() {
    runTest("(continuation a)", "a");
  }
  
  public void testLambda() {
    runTest("(continuation (lambda x k (k x)))", "(lambda x x)");
  }
  
  public void testApplication() {
    runTest("(c d (lambda $0 (a b $0 continuation)))", "(a b (c d))");
  }

  private void runTest(String expected, String input) {
    ExpressionParser<Expression2> p = ExpressionParser.expression2();
    ExpressionSimplifier s = ExpressionSimplifier.lambdaCalculus();
    Expression2 inputExp = p.parse(input);
    Expression2 expectedExp = s.apply(p.parse(expected));

    Expression2 continuation = Expression2.constant("continuation");
    
    Expression2 resultExp = s.apply(CpsTransform.apply(inputExp, continuation));
    
    assertEquals(expectedExp, resultExp);
  }
}
