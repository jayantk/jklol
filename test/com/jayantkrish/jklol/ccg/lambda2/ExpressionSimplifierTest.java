package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

public class ExpressionSimplifierTest extends TestCase {

  ExpressionSimplifier simplifier, canonicalizer, conjunction;

  public void setUp() {
    simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule()));
    canonicalizer = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule()));
    conjunction = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
  }
  
  public void testSimplifyLambda() {
    runTest(simplifier, "(foo bar baz)", "(foo bar baz)");
  }
  
  public void testSimplifyLambda2() {
    runTest(simplifier, "((lambda x (x bar baz)) foo)", "(foo bar baz)");
  }
  
  public void testSimplifyLambda3() {
    runTest(simplifier, "((lambda x y (x bar baz y)) foo)", "(lambda y (foo bar baz y))");
  }
  
  public void testSimplifyLambda4() {
    runTest(simplifier, "(((lambda x (lambda y (x bar baz y))) foo) abcd)", "(foo bar baz abcd)");
  }
  
  public void testSimplifyLambda5() {
    runTest(simplifier, "(((lambda x (lambda x (x bar baz x))) foo) abcd)", "(abcd bar baz abcd)");
  }

  public void testSimplifyLambda6() {
    runTest(simplifier, "(((lambda x (lambda x (x bar baz x))) bar) (abcd ab))", "((abcd ab) bar baz (abcd ab))");
  }

  public void testSimplifyLambda7() {
    runTest(simplifier, "(((lambda x y (x bar baz y)) foo) ((lambda x x) abcd))", "(foo bar baz abcd)");
  }
  
  public void testSimplifyLambda8() {
    runTest(canonicalizer, "((lambda $0 (lambda $1 ($0 $1))) (lambda $1 (lambda $2 (loc:<lo,<lo,t>> $2 $1)) ))",
        "(lambda $0 (lambda $1 (loc:<lo,<lo,t>> $1 $0)))");
  }
  
  public void testSimplifyLambda9() {
    runTest(simplifier, "((lambda x (lambda y (x bar baz y))) foo abcd)", "(foo bar baz abcd)");
  }

  public void testCanonicalize1() {
    runTest(canonicalizer, "(foo bar baz)", "(foo bar baz)");
  }

  public void testCanonicalize2() {
    runTest(canonicalizer, "(lambda x (foo x))", "(lambda $0 (foo $0))");
  }
  
  public void testCanonicalize3() {
    runTest(canonicalizer, "(lambda x (foo (lambda y (x y))))", "(lambda $0 (foo (lambda $1 ($0 $1))))");
  }

  public void testCanonicalize4() {
    runTest(canonicalizer, "(lambda x (foo (lambda y (x y)) (lambda z (x z))))", "(lambda $0 (foo (lambda $1 ($0 $1)) (lambda $1 ($0 $1))))");
  }
  
  public void testCanonicalize5() {
    runTest(canonicalizer, "(lambda x (((lambda y (x y)) foo) ((lambda z (x z)) bar)))", "(lambda $0 (($0 foo) ($0 bar)))");
  }

  public void testConjunction1() {
    runTest(conjunction, "(and:<t*,t> x y z)", "(and:<t*,t> x y z)");
  }
  
  public void testConjunction2() {
    runTest(conjunction, "(and:<t*,t> x (and:<t*,t> y z))", "(and:<t*,t> x y z)");
  }
  
  public void testConjunction3() {
    runTest(conjunction, "(and:<t*,t> z (and:<t*,t> y x))", "(and:<t*,t> x y z)");
  }
  
  public void testConjunction4() {
    runTest(conjunction, "((lambda $0 (and:<t*,t> y x $0)) (and:<t*,t> z))", "(and:<t*,t> x y z)");
  }
  
  public void testConjunction5() {
    runTest(conjunction, "((lambda $0 (lambda $1 ($0 $1))) (lambda $1 "
        + "(lambda $2 (loc:<lo,<lo,t>> $2 $1)) ))",
        "(lambda $0 (lambda $1 (loc:<lo,<lo,t>> $1 $0)))");
  }

  private void runTest(ExpressionSimplifier simp, String input, String expected) {
    ExpressionParser<Expression2> parser = ExpressionParser.expression2();
    Expression2 inputExpression = parser.parseSingleExpression(input);
    Expression2 expectedExpression = parser.parseSingleExpression(expected);
    
    Expression2 simplified = simp.apply(inputExpression);
    System.out.println(simplified);
    System.out.println(expectedExpression);
    assertEquals(expectedExpression, simplified);
  }

}
