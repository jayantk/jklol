package com.jayantkrish.jklol.cvsm.eval;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cvsm.eval.Value.StringValue;

public class EvalTest extends TestCase {

  Eval eval;
  ExpressionParser<SExpression> parser;

  public void setUp() {
    eval = new Eval();
    parser = ExpressionParser.sExpression();
  }

  public void testCar() {
    String value = runTestString("(car (cons \"foo\" \"bar\"))");
    assertEquals("foo", value);
  }

  public void testCdr() {
    String value = runTestString("(car (cdr (cons \"foo\" (cons \"bar\" \"baz\"))))");
    assertEquals("bar", value);
  }

  public void testLambda() {
    String value = runTestString("(define cadr (lambda (x) (car (cdr x)))) (cadr (cons \"foo\" (cons \"bar\" \"baz\")))");
    assertEquals("bar", value);
  }

  public void testDefine() {
    String value = runTestString("(define x \"abcd\") x");
    assertEquals("abcd", value);
  }

  public void testList() {
    String value = runTestString("(car (cdr (list \"a\" \"b\" \"c\")))");
    assertEquals("b", value);
  }

  public void testIf1() {
    String value = runTestString("(if (nil? (list)) \"true\" \"false\")");
    assertEquals("true", value);
  }

  public void testIf2() {
    String value = runTestString("(if (nil? (list \"a\" \"b\")) \"true\" \"false\")");
    assertEquals("false", value);
  }

  public void testRecursion() {
    // "(define predict-labels (lambda (seq) (if (nil? seq) (list) (let ((prev-seq (predict-labels (cdr seq)))) (cons (amb"
    Value value = runTest("(define predict-labels (lambda (seq) (if (nil? seq) (list) (cons \"a\" (predict-labels (cdr seq)))))) (predict-labels (list \"x\" \"y\"))");
    System.out.println(value);
  }

  private Value runTest(String expressionString) {
    String wrappedExpressionString = "(begin " + expressionString + ")";
    return eval.eval(parser.parseSingleExpression(wrappedExpressionString), Environment.empty())
        .getValue();
  }

  private String runTestString(String expressionString) {
    Value value = runTest(expressionString);
    return ((StringValue) value).getValue();
  }
}
