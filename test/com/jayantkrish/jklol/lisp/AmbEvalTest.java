package com.jayantkrish.jklol.lisp;

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
    Object value = runTest("(get-best-assignment (list (amb (list 1 2) (list 1 2)) (amb (list 1 2 3) (list 1 2 1))))");
    Object expected = runTest("(list 2 2)");
    assertEquals(expected, value);
  }

  public void testAmbIf() {
    fail();
  }
  
  public void testAmbMarginals1() {
    Object value = runTest("(get-marginals (amb (list 1 2) (list 2 2)))");
    Object expected = runTest("(cons (list 1 2) (list 0.5 0.5))");
    assertEquals(expected, value);
  }

  public void testAddWeight1() {
    int value = runTestInt("(define x (amb (list 1 2) (list 1 2))) (define y (amb (list 1 2) (list 1 2))) (add-weight (not (= (+ x y) 2)) 0) (get-best-assignment x)");
    assertEquals(1, value);
  }

  public void testAddWeight2() {
    int value = runTestInt("(define x (amb (list 1 2) (list 1 2))) (define y (amb (list 1 2) (list 1 2))) (add-weight (and (= x 1) (= y 1)) 8) (get-best-assignment x)");
    assertEquals(1, value);
  }

  public void testAmbLambda() {
    int value = runTestInt("(define x (amb (list 1 2) (list 1 6))) (define foo (lambda (x) (add-weight (= x 1) 4))) (foo x) (foo x) (get-best-assignment x)");
    assertEquals(1, value);
  }
  
  public void testAmbLambda2() {
    int value = runTestInt("(define x (amb (list 1 2) (list 1 6))) (define foo (lambda (x) (begin (define y (amb (list 1 2) (list 1 4))) (add-weight (not (= (+ x y) 3)) 0)))) (foo x) (foo x) (get-best-assignment x)");
    assertEquals(1, value);
  }

  public void testAmbLambda3() {
    int value = runTestInt("(define foo (amb (list (lambda (x) (+ x 1)) (lambda (x) (+ x 2))) (list 1 2))) (define x (foo 1)) (get-best-assignment x)");
    assertEquals(3, value);
  }
  
  public void testAmbLambda4() {
    int value = runTestInt("(define foo (amb (list (lambda (x) (+ x 1)) (lambda (x) (+ x 2))) (list 1 2))) (define x (foo (amb (list 1 2) (list 2 3)))) (add-weight (= x 4) 0) (get-best-assignment x \"dual-decomposition\")");
    assertEquals(3, value);
  }

  public void testRecursion() {
    String program = "(define word-factor (lambda (label word) (begin " +
    		"(add-weight (and (= word \"car\") (= label \"N\")) 2) " +
    		"(add-weight (and (= word \"goes\") (= label \"V\")) 3))))" +
    		"" +
    		"(define transition-factor (lambda (cur-label next-label)" +
    		"(add-weight (and (= next-label \"N\") (= cur-label \"N\")) 2))) " +
    		"" +
    		"(define sequence-tag (lambda (input-seq) (if (nil? input-seq) (list) (begin " +
    		"(define cur-label (amb (list \"N\" \"V\") (list 1 1)))" +
    		"(word-factor cur-label (car input-seq))" +
    		"(if (not (nil? (cdr input-seq)))" +
    		" (begin (define next-label (sequence-tag (cdr input-seq)))" +
    		"        (transition-factor cur-label next-label)" +
    		"        cur-label)" +
    		" cur-label)))))" +
    		"" +
    		"(define x (get-best-assignment (sequence-tag (list \"car\"))))" +
    		"(define y (get-best-assignment (sequence-tag (list \"goes\" \"car\")))) " +
    		"(define z (get-best-assignment (sequence-tag (list \"the\" \"car\"))))" +
    		"(list x y z)";

    Object value = runTest(program);
    Object expected = runTest("(list \"N\" \"V\" \"N\")");
    assertEquals(expected, value);
  }
  
  public void testCfg() {
    String program = "(define label-list (list \"DT\" \"NN\" \"JJ\" \"VB\")) " +
    		"(define new-label (lambda () (amb label-list (list 1 1 1 1))))" +
    		"" +
    		"(define word-factor (lambda (label word) (begin " +
    		"(add-weight (and (= word \"car\") (= label \"NN\")) 2) " +
    		"(add-weight (and (= word \"big\") (= label \"JJ\")) 2) " +
    		"(add-weight (and (= word \"goes\") (= label \"VB\")) 3))))" +
    		"" +
    		"(define transition-factor (lambda (left right root) (begin " +
    		"(add-weight (and (= left \"DT\") (and (= right \"NN\") (= root \"NN\"))) 2)" +
    		"(add-weight (and (= left \"JJ\") (and (= right \"NN\") (= root \"NN\"))) 2))))" +
    		"" +
    		"(define first-n (lambda (seq n) (if (= n 0) (list) (cons (car seq) (first-n (cdr seq) (- n 1))))))" +
    		"(define remainder-n (lambda (seq n) (if (= n 0) seq (remainder-n (cdr seq) (- n 1)))))" +
    		"(define 1-to-n (lambda (n) (if (= n 0) (list) (cons n (1-to-n (- n 1))))))" +
    		"(define map (lambda (f seq) (if (nil? seq) (list) (cons (f (car seq)) (map f (cdr seq))))))" +
    		"(define length (lambda (seq) (if (nil? seq) 0 (+ (length (cdr seq)) 1))))" +
    		"" +
    		"(define cfg-parse (lambda (input-seq) (begin " +
    		"(define label-var (new-label))" +
    		"(if (= (length input-seq) 1) " +
    		"    (word-factor label-var (car input-seq))" +
    		"    (begin " +
    		"       (define split-list (1-to-n (- (length input-seq) 1)))" +
    		"       (define choice-var (amb split-list))" +
    		"       (map (lambda (i) (do-split input-seq i label-var choice-var)) split-list)))" +
    		"label-var)))" +
    		"" +
    		"(define do-split (lambda (seq i root-var choice-var) (begin" +
    		"  (define left-seq (first-n seq i))" +
    		"  (define right-seq (remainder-n seq i))" +
    		"  (define left-parse-root (cfg-parse left-seq))" +
    		"  (define right-parse-root (cfg-parse right-seq))" +
    		"  (define cur-root (new-label))" +
    		"  (transition-factor left-parse-root right-parse-root cur-root)" +
    		"  (add-weight (and (= choice-var i) (not (= cur-root root-var))) 0))))" +
    		"" +
    		"(get-best-assignment (cfg-parse (list \"big\" \"car\")) \"junction-tree\")";

    String value = runTestString(program);
    System.out.println(value);
  }

  private Object runTest(String expressionString) {
    String wrappedExpressionString = "(begin " + expressionString + ")";
    return eval.eval(parser.parseSingleExpression(wrappedExpressionString),
        AmbEval.getDefaultEnvironment()).getValue();
  }

  private String runTestString(String expressionString) {
    return (String) runTest(expressionString);
  }

  private int runTestInt(String expressionString) {
    return (Integer) runTest(expressionString);
  }
}
