package com.jayantkrish.jklol.cvsm.eval;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

public class AmbEvalTest extends TestCase {

  Eval eval;
  ExpressionParser<SExpression> parser;

  public void setUp() {
    eval = new AmbEval();
    parser = ExpressionParser.sExpression();
  }

  public void testAmb() {
    String value = runTestString("(get-best-assignment (amb (list \"a\" \"b\" \"c\") (list 1 2 1)))");
    assertEquals("b", value);
  }

  public void testAmb2() {
    int value = runTestInt("(get-best-assignment (+ 1 (amb (list 1 2 3) (list 1 2 1))))");
    assertEquals(3, value);
  }

  public void testAmb3() {
    int value = runTestInt("(get-best-assignment (+ (amb (list 1 2) (list 1 2)) (amb (list 1 2 3) (list 1 2 1))))");
    assertEquals(4, value);
  }

  public void testAmb4() {
    int value = runTestInt("(get-best-assignment (list (amb (list 1 2) (list 1 2)) (amb (list 1 2 3) (list 1 2 1))))");
    assertEquals(4, value);
  }

  /*
  public void testAmb5() {
    
    "(define x (amb (list 1 2) (list 1 2))) (define y (+ x 1))";
    
    "(define sequence-tag (lambda input-seq (if (nil? input-seq) (list) (let ((prev-labels (sequence-tag (cdr input-seq))) (predicted-label (predict label-list (list (car prev-labels) (car input-seq))))) (cons predicted-label prev-labels))"
    
  }
  */

  private Object runTest(String expressionString) {
    String wrappedExpressionString = "(begin " + expressionString + ")";
    return eval.eval(parser.parseSingleExpression(wrappedExpressionString),
        LispEval.getDefaultEnvironment()).getValue();
  }

  private String runTestString(String expressionString) {
    return (String) runTest(expressionString);
  }

  private int runTestInt(String expressionString) {
    return (Integer) runTest(expressionString);
  }
}
