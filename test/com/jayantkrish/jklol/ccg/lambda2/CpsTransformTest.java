package com.jayantkrish.jklol.ccg.lambda2;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

public class CpsTransformTest extends TestCase {

  public void testConstant() {
    runTest("(continuation a)", "a");
  }
  
  public void testLambda() {
    runTest("(continuation (lambda (k x) (k x)))", "(lambda (x) x)");
  }

  public void testApplication() {
    runTest("(c (lambda ($0) (a continuation b $0)) d)", "(a b (c d))");
  }
  
  public void testSomething1() {
    runTest("(count:<<a,t>,i> continuation (lambda ($0 $1) (animal:<a,t> (lambda ($2) (eats:<a,<a,t>> (lambda ($3) (and:<t*,t> $0 $2 $3)) $1 \"bears\":a)) $1)))",
        "(count:<<a,t>,i> (lambda ($0) (and:<t*,t> (animal:<a,t> $0) (eats:<a,<a,t>> $0 \"bears\":a))))");
  }
  
  public void testSomething2() {
    runTest("(continuation (lambda ($0 $1) (eats:<a,<a,t>> (lambda ($2) (sun:<a,t> (lambda ($3) (and:<t*,t> $0 $2 $3)) $1)) $1 \"grasses and other plants\":a)))",
        "(lambda ($0) (and:<t*,t> (eats:<a,<a,t>> $0 \"grasses and other plants\":a) (sun:<a,t> $0)))");
  }
  
  public void testQuote1() {
    runTest("(continuation (quote (lambda (x) (foo x))))", "(quote (lambda (x) (foo x)))");
  }
  
  public void testQuote2() {
    runTest("(foo continuation (quote a))", "((lambda (x) (foo x)) (quote a))");
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
