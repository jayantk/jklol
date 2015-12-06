package com.jayantkrish.jklol.lisp;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.util.IndexedList;

public class LispEvalTest extends TestCase {

  LispEval eval;
  ExpressionParser<SExpression> parser;

  public void setUp() {
    IndexedList<String> symbolTable = LispEval.getInitialSymbolTable();
    eval = new LispEval(symbolTable);
    parser = ExpressionParser.sExpression(symbolTable);
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
    int value = runTestInt("(define length (lambda (seq) (if (nil? seq) 0 (+ (length (cdr seq)) 1)))) (length (list \"a\" \"b\" \"c\"))");
    assertEquals(3, value);
  }

  /*
  public void testRecursion() {
    // "(define predict-labels (lambda (seq) (if (nil? seq) (list) (let ((prev-seq (predict-labels (cdr seq)))) (cons (amb"
    Value value = runTest("(define predict-labels (lambda (seq) (if (nil? seq) (list) (cons \"a\" (predict-labels (cdr seq)))))) (predict-labels (list \"x\" \"y\"))");
    System.out.println(value);
  }
  */

  private Object runTest(String expressionString) {
    String wrappedExpressionString = "(begin " + expressionString + ")";
    return eval.eval(parser.parse(wrappedExpressionString),
        LispEval.getDefaultEnvironment(eval.getSymbolTable())).getValue();
  }

  private String runTestString(String expressionString) {
    return (String) runTest(expressionString);
  }

  private int runTestInt(String expressionString) {
    return (Integer) runTest(expressionString);
  }
}
