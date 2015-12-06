package com.jayantkrish.jklol.lisp;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.util.IndexedList;

public class AmbEvalTest extends TestCase {

  AmbEval eval;
  ExpressionParser<SExpression> parser;

  private static final double TOLERANCE = 1e-2;

  public void setUp() {
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    eval = new AmbEval(symbolTable);
    parser = ExpressionParser.sExpression(symbolTable);
  }

  public void testAmb() {
    String value = runTestString("(get-best-value (amb (list \"a\" \"b\" \"c\") (list 1 2 1)))");
    assertEquals("b", value);
  }

  public void testAmb2() {
    int value = runTestInt("(get-best-value (+ 1 (amb (list 1 2 3) (list 1 2 1))))");
    assertEquals(3, value);
  }

  public void testAmb3() {
    int value = runTestInt("(get-best-value (+ (amb (list 1 2) (list 1 2)) (amb (list 1 2 3) (list 1 2 1))))");
    assertEquals(4, value);
  }

  public void testAmb4() {
    Object value = runTest("(get-best-value (list (amb (list 1 2) (list 1 2)) (amb (list 1 2 3) (list 1 2 1))))");
    Object expected = runTest("(list 2 2)");
    assertEquals(expected, value);
  }
  
  public void testIf() {
    String value = runTestString("(if (= (list 1 2) (list 1 2)) \"true\" \"false\")");
    assertEquals("true", value);
  }

  // These tests are operational, but fails since conditionals are not
  // accepted in the current implementation of the language.
  /*
  public void testIfAmb1() {
    String value = runTestString("(get-best-value (if (= (amb (list 1 2) (list 1 2)) 1) \"true\" \"false\"))");
    assertEquals("false", value);
  }

  public void testIfAmb2() {
    String value = runTestString("(get-best-value (if (= (amb (list 1 2) (list 1 2)) 1) (begin (add-weight (= 1 1) 4.0) \"true\") \"false\"))");
    assertEquals("true", value);
  }

  public void testIfAmb3() {
    String value = runTestString("(get-best-value (if (= (amb (list 1 2) (list 1 2)) 1) (amb (list \"a\" \"b\") (list 2 4)) \"false\"))");
    assertEquals("b", value);
  }

  public void testIfAmb4() {
    String value = runTestString("(define x (amb (list \"a\" \"b\") (list 2 1))) (get-best-value (if (= (amb (list 1 2) (list 1 2)) 1) (add-weight (= x \"b\") 5.0) \"false\")) (get-best-value x)");
    assertEquals("b", value);
  }
  
  public void testIfAmb5() {
    String value = runTestString("(define x (amb (list (list) (list \"a\" \"b\")) (list 2 1))) " +
    		"(get-best-value (if (not (nil? x)) (car x) \"nil\"))");
    assertEquals("nil", value);
  }
  */
  
  public void testLet() {
    Object value = runTest("(let ((x 123) (y 456)) (list x y))");
    Object expected = runTest("(list 123 456)");
    assertEquals(expected, value);
  }

  public void testAmbMarginals1() {
    Object value = runTest("(get-marginals (amb (list 1 2) (list 2 2)))");
    Object expected = runTest("(list (list 1 2) (list 0.5 0.5))");
    assertEquals(expected, value);
  }
  
  public void testVariableElimination() {
    String program = "(define label-list (list \"DT\" \"NN\" \"JJ\" \"VB\")) " +
    		"(define new-label (lambda () (amb label-list (list 1 1 1 1))))" +
    		"" +
        "(define transition-factor (lambda (left right root) (begin " +
    		"(add-weight (and (= left \"DT\") (and (= right \"NN\") (= root \"NN\"))) 2)" +
    		"(add-weight (and (= left \"JJ\") (and (= right \"NN\") (= root \"NN\"))) 3))))" +
    		"" +
    		"(define x (new-label))" +
    		"(define y (new-label))" +
    		"(define z (new-label))" +
    		"(transition-factor x y z)" +
    		"(get-best-value (list x y z))";

    Object value = runTest(program);
    Object expected = runTest("(list \"JJ\" \"NN\" \"NN\")");
    assertEquals(expected, value);
  }

  public void testAddWeight1() {
    int value = runTestInt("(define x (amb (list 1 2) (list 1 2))) (define y (amb (list 1 2) (list 1 2))) (add-weight (not (= (+ x y) 2)) 0) (get-best-value x)");
    assertEquals(1, value);
  }

  public void testAddWeight2() {
    int value = runTestInt("(define x (amb (list 1 2) (list 1 2))) (define y (amb (list 1 2) (list 1 2))) (add-weight (and (= x 1) (= y 1)) 8) (get-best-value x)");
    assertEquals(1, value);
  }

  public void testAmbLambda() {
    int value = runTestInt("(define x (amb (list 1 2) (list 1 6))) (define foo (lambda (x) (add-weight (= x 1) 4))) (foo x) (foo x) (get-best-value x)");
    assertEquals(1, value);
  }
  
  public void testAmbLambda2() {
    int value = runTestInt("(define x (amb (list 1 2) (list 1 6))) (define foo (lambda (x) (begin (define y (amb (list 1 2) (list 1 4))) (add-weight (not (= (+ x y) 3)) 0)))) (foo x) (foo x) (get-best-value x)");
    assertEquals(1, value);
  }

  public void testAmbLambda3() {
    int value = runTestInt("(define foo (amb (list (lambda (x) (+ x 1)) (lambda (x) (+ x 2))) (list 1 2))) (define x (foo 1)) (get-best-value x)");
    assertEquals(3, value);
  }
  
  public void testAmbLambda4() {
    int value = runTestInt("(define foo (amb (list (lambda (x) (+ x 1)) (lambda (x) (+ x 2))) (list 1 2))) (define x (foo (amb (list 1 2) (list 2 3)))) (add-weight (= x 4) 0) (get-best-value x)");
    assertEquals(3, value);
  }

  public void testAmbLambda5() {
    int value = runTestInt(
        // "(define rand-op (lambda () (amb (list + * - (lambda (x y) x) (lambda (x y) y))))) " +
        "(define rand-op (lambda () (amb (list + * (lambda (x y) x) (lambda (x y) y))))) " +
        "(define unroll-op (lambda (op-func depth) (begin (define cur-op (op-func)) " +
        "  (if (= depth 1) (lambda (x y) (cur-op x y)) " +
        "                  (begin (define unrolled1 (unroll-op op-func (- depth 1))) " +
        "                         (define unrolled2 (unroll-op op-func (- depth 1)))" +
        "                         (lambda (x y) (cur-op (unrolled1 x y) (unrolled2 x y))))))))" +
        "(define func-to-learn (unroll-op rand-op 2)) " +
        "(add-weight (= (func-to-learn 1 2) 4) 2.0)" +
        "(add-weight (= (func-to-learn 3 2) 8) 2.0)" +
        "(add-weight (= (func-to-learn 4 7) 35) 2.0)" +
        "(get-best-value (func-to-learn 2 3))");
    assertEquals(9, value);
  }

  public void testAmbLambda6() {
    int value = runTestInt(
        "(define rand-int (lambda () (amb (list 1 2 3 4 5 6 7 8 9 10))))" +
        "(define poly (lambda (degree) (begin (define cur-root (rand-int)) " +
        "  (if (= degree 1) (lambda (x) (- x cur-root)) " +
        "                  (begin (define unrolled (poly (- degree 1))) " +
        "                         (lambda (x) (* (- x cur-root) (unrolled x))))))))" +
        "(define func-to-learn (poly 2)) " +
        "(add-weight (= (func-to-learn 1) 0) 2.0)" +
        "(add-weight (= (func-to-learn 2) 0) 2.0)" +
        "(get-best-value (func-to-learn 4))");
    assertEquals(6, value);
  }

  public void testNewFgScope() {
    String value = runTestString("(new-fg-scope (get-best-value (amb (list \"a\" \"b\" \"c\") (list 1 2 1))))");
    assertEquals("b", value);
  }

  public void testSquareLoss() {
    Object value = runTest(
        "(define sum-outcomes (lambda (o p) (if (nil? o) 0.0 (+ (* (car o) (car p)) (sum-outcomes (cdr o) (cdr p))))))" +
        "(define sq-loss (lambda (m) (lambda (a) (define marginals (get-marginals (* (- a m) (- a m)))) (sum-outcomes (car marginals) (car (cdr marginals))))))" +
        "((sq-loss 2) (amb (list 1.0 2.0 3.0 4.0 5.0)))");
    assertEquals(3.0, value);
  }

  public void testLogLoss() {
    Object value = runTest(
        "(define get-item-prob (lambda (i o p) (if (nil? o) 0.0 (if (= i (car o)) (car p) (get-item-prob i (cdr o) (cdr p))))))" +
        "(define log-loss (lambda (item) (lambda (a) (define marginals (get-marginals a)) (log (get-item-prob item (car marginals) (car (cdr marginals)))))))" +
        "((log-loss \"a\") (amb (list \"a\" \"b\" \"c\") (list 2 1 1)))");
    assertEquals(Math.log(0.5), (Double) value, 0.000001);
  }

  public void testRecursion() {
    String program = "(define word-factor (lambda (label word) (begin " +
    		"(add-weight (and (= word \"car\") (= label \"N\")) 2) " +
    		"(add-weight (and (= word \"goes\") (= label \"V\")) 3))))" +
    		"" +
    		"(define transition-factor (lambda (cur-label next-label)" +
    		"(add-weight (and (= next-label \"N\") (= cur-label \"N\")) 2))) " +
    		"" +
    		"(define sequence-tag (lambda (input-seq) (if (nil? input-seq) (lifted-list) (begin " +
    		"(define cur-label (amb (list \"N\" \"V\") (list 1 1)))" +
    		"(define next-labels (sequence-tag (lifted-cdr input-seq))) " +
    		"(word-factor cur-label (car input-seq))" +
    		"(if (not (nil? (cdr input-seq)))" +
     		" (begin " +
    		"        (define next-label (lifted-car next-labels))" +
    		"        (transition-factor cur-label next-label)" +
    		"        (lifted-cons cur-label next-labels))" +
    		" (lifted-cons cur-label next-labels))))))" +
    		"" +
    		"(define x (get-best-value (sequence-tag (list \"car\"))))" +
    		"(define y (get-best-value (sequence-tag (list \"goes\" \"car\")))) " +
    		"(define z (get-best-value (sequence-tag (list \"the\" \"car\"))))" +
    		"(list x y z)";

    Object value = runTest(program);
    Object expected = runTest("(list (list \"N\") (list \"V\" \"N\") (list \"N\" \"N\"))");
    assertEquals(expected, value);
  }

  // This test is operational, but fails since conditionals are not
  // accepted in the current implementation of the language.
  /*
  public void testCfg() {
    String program = "(define label-list (list \"DT\" \"NN\" \"JJ\" \"VB\")) " +
    		"(define new-label (lambda () (amb label-list (list 1 1 1 1))))" +
    		"" +
    		"(define word-factor (lambda (label word) (begin " +
    		"(add-weight (and (= word \"car\") (= label \"NN\")) 2) " +
    		"(add-weight (and (= word \"big\") (= label \"JJ\")) 2) " +
    		"(add-weight (and (= word \"the\") (= label \"JJ\")) 0.5) " +
    		"(add-weight (and (= word \"the\") (= label \"DT\")) 2) " +
    		"(add-weight (and (= word \"goes\") (= label \"VB\")) 3))))" +
    		"" +
    		"(define transition-factor (lambda (left right root) (begin " +
    		"(add-weight (and (= left \"DT\") (and (= right \"NN\") (= root \"NN\"))) 2)" +
    		"(add-weight (and (= left \"JJ\") (and (= right \"NN\") (= root \"NN\"))) 2))))" +
    		"" +
    		"(define first-n (lambda (seq n) (if (= n 0) (list) (cons (car seq) (first-n (cdr seq) (- n 1))))))" +
    		"(define remainder-n (lambda (seq n) (if (= n 0) seq (remainder-n (cdr seq) (- n 1)))))" +
    		"(define 1-to-n (lambda (n) (1-to-n-helper n 1)))" +
    		"(define 1-to-n-helper (lambda (n i) (if (= (+ n 1) i) (list) (cons i (1-to-n-helper n (+ i 1))))))" +
    		"(define get-ith-element (lambda (i seq) (if (= i 0) (car seq) (get-ith-element (- i 1) (cdr seq)))))" +
    		"(define map (lambda (f seq) (if (nil? seq) (list) (cons (f (car seq)) (map f (cdr seq))))))" +
    		"(define length (lambda (seq) (if (nil? seq) 0 (+ (length (cdr seq)) 1))))" +
    		"" +
    		"(define cfg-parse (lambda (input-seq) (begin " +
    		"(define label-var (new-label))" +
    		"(if (= (length input-seq) 1) " +
    		"    (begin (word-factor label-var (car input-seq))" +
    		"           (list label-var (car input-seq)))" +
    		"    (begin " +
    		"       (define split-list (1-to-n (- (length input-seq) 1)))" +
    		"       (define choice-var (amb split-list))" +
    		"       (list label-var choice-var (map (lambda (i) (do-split input-seq i label-var choice-var)) split-list)))))))" +
    		"" +
    		"(define do-split (lambda (seq i root-var choice-var) (begin" +
    		"  (define left-seq (first-n seq i))" +
    		"  (define right-seq (remainder-n seq i))" +
    		"  (define left-parse (cfg-parse left-seq))" +
    		"  (define right-parse (cfg-parse right-seq))" +
    		"  (define left-parse-root (car left-parse))" +
    		"  (define right-parse-root (car right-parse))" +
    		"  (if (choice-var == i)" +
    		"    (begin (transition-factor left-parse-root right-parse-root root-var)" +
    		"           (list cur-root left-parse right-parse))))))" +
    		"" +
    		"(define decode-parse (lambda (chart) (if (= (length chart) 2)" +
    		"   chart" +
    		"   (begin" +
    		"      (define chosen-subtree (get-ith-element (- (car (cdr chart)) 1) (car (cdr (cdr chart)))))" +
    		"      (list (car chart) (decode-parse (car (cdr chosen-subtree))) (decode-parse (car (cdr (cdr chosen-subtree)))))))))" +
    		"" +
    		"(decode-parse (get-best-value (cfg-parse (list \"the\" \"big\" \"car\")) \"junction-tree\"))";

    Object value = runTest(program);
    Object expected = runTest("(list \"NN\" (list \"DT\" \"the\") (list \"NN\" (list \"JJ\" \"big\") (list \"NN\" \"car\")))");
    assertEquals(expected, value);

    // CFGs can't be correctly implemented in this language as is. The above
    // program includes decisions in un-chosen subtrees in the score of a parse,
    // which is not correct.
    fail();
  }
  */
  
  public void testFeaturizedClassifier() {
    String program = "(define label-list (list #t #f))" +
    		"(define feature-list (list \"A\" \"B\" \"C\"))" +
    		"(define feature-func (make-feature-factory feature-list))" +
    		"" +
    		"(define classifier-family (lambda (parameters) " +
    		"  (lambda (feature-vec)" +
    		"    (define label (amb label-list))" +
    		"    (make-featurized-classifier label feature-vec parameters)" +
    		"    label)))" +
    		"" +
    		"(define require (lambda (x) (add-weight (not x) 0.0)))" +
    		"" +
    		"(define vec1 (feature-func (list (list \"A\" 1.0) (list \"B\" 2.0))))" +
    		"(define vec2 (feature-func (list (list \"A\" 1.0) (list \"C\" 1.0))))" +
    		"(define training-data (list (list (list vec1) (lambda (label) (require (= label #t))))" +
    		"                            (list (list vec2) (lambda (label) (require (= label #f))))" +
    		"  ))" +
    		"(define classifier (classifier-family (make-featurized-classifier-parameters (list label-list) feature-list)))" +
    		"(define best-params (opt classifier-family" +
    		"   (make-featurized-classifier-parameters (list label-list) feature-list) training-data))" +
    		"(define classifier (classifier-family best-params))" + 
    		"(list (get-best-value (classifier vec1))" +
    		"      (get-best-value (classifier vec2)))";

    Object value = runTest(program);
    Object expected = runTest("(list #t #f)");
    assertEquals(value, expected);
  }

  public void testOpt1() {
    String program = "(define label-list (list #t #f))" +
    		"(define discrete-family (lambda (parameters) " +
    		"  (lambda () " +
    		"    (define label (amb label-list))" +
    		"    (make-indicator-classifier label parameters)" +
    		"    label)))" +
    		"" +
    		"(define require (lambda (x) (add-weight (not x) 0.0)))" +
    		"" +
    		"(define training-data (list (list (list) #t)" +
    		"                             (list (list) #t)" +
        "                             (list (list) #t)" +
        "                             (list (list) #f)))" +
        "" +
    		"(define best-params (opt discrete-family (make-indicator-classifier-parameters (list label-list)) training-data))" +
    		"(define factor (discrete-family best-params))" +
    		"(define marginals (get-marginals (factor)))" +
    		"(define probs (car (cdr marginals)))" +
    		"(define tp (car probs))" +
    		"(define fp (car (cdr probs)))" +
    		"(+ (* (- tp 0.75) (- tp 0.75)) (* (- fp 0.25) (- fp 0.25)))";

    Double value = runTestDouble(program);
    assertEquals(0.0, value, TOLERANCE);
  }

  public void testOpt2() {
    String program = "(define label-list (list #t #f))" +
    		"(define label-list2 (list \"A\" \"B\" \"C\"))" +
        "(define discrete-family (lambda (parameters) " +
        "  (lambda () " +
        "    (define label1 (amb label-list))" +
        "    (define label2 (amb label-list2))" +
        "    (make-indicator-classifier (lifted-list label1 label2) parameters)" +
        "    (lifted-list label1 label2))))" +
        "" +
        "(define require1 (lambda (x) (add-weight (not x) 0.0)))" +
        "" +
        "(define training-data (list (list (list) (lambda (label) (require1 (= (lifted-car label)	#t))))" +
        "                             (list (list) (lambda (label) (require1 (= (lifted-car label) #t))))" +
        "                             (list (list) (lambda (label) (require1 (= (lifted-car label) #t))))" +
        "                             (list (list) (lambda (label) (require1 (= (lifted-car label) #f))))))" +
        "" +
        "(define best-params (opt discrete-family (make-indicator-classifier-parameters (list label-list label-list2)) training-data))" +
        "(define factor (discrete-family best-params))" +
        "(define dist (factor))" +
        "(define var1 (lifted-car dist))" +
        "(define var2 (lifted-car (lifted-cdr dist)))" +
        "(define marginals (get-marginals var1))" +
        "(display marginals)" +
        "(display (get-marginals var2))" +
        "(define probs (car (cdr marginals)))" +
        "(define tp (car probs))" +
        "(define fp (car (cdr probs)))" +
        "(+ (* (- tp 0.75) (- tp 0.75)) (* (- fp 0.25) (- fp 0.25)))";

    Double value = runTestDouble(program);
    assertEquals(0.0, value, TOLERANCE);
  }

  public void testOpt3() {
    String program = "(define label-list (list #t #f))" +
    		"(define word-list (list \"A\" \"B\" \"C\"))" +
    		"" +
    		"(define sequence-family (lambda (parameters)" +
    		"  (let ((word-parameters (get-ith-parameter parameters 0))" +
    		"        (transition-parameters (get-ith-parameter parameters 1)))" +
    		"  (define sequence-tag (lambda (seq)" +
    		"    (if (nil? seq) " +
    		"        (lifted-list)" +
    		"        (begin " +
    		"          (define cur-label (amb label-list)) "+ 
    		"          (define cur-word (amb word-list)) " +
    		"          (add-weight (not (= cur-word (car seq))) 0.0)" +
    		"          (make-indicator-classifier (lifted-list cur-word cur-label) word-parameters)" +
    		"          (if (nil? (cdr seq))" +
    		"            (lifted-list cur-label)" +
    		"            (begin (define remaining-labels (sequence-tag (cdr seq)))" +
    		"              (make-indicator-classifier (lifted-list cur-label (lifted-car remaining-labels)) transition-parameters)" +
    		"              (lifted-cons cur-label remaining-labels)))))))" +
    		"  sequence-tag)))" +
    		"" +
    		"(define require-seq-equal (lambda (predicted actual)" +
    		"  (if (nil? actual) " +
    		"      #t" +
    		"     (begin (add-weight (not (= (lifted-car predicted) (car actual))) 0.0)" +
    		"        (require-seq-equal (lifted-cdr predicted) (cdr actual))))))" +
    		"" +
    		"(define training-data (list (list (list (list \"A\" \"B\" \"C\")) (lambda (label-seq) (require-seq-equal label-seq (list #t #f #t))))" +
    		"                            (list (list (list \"C\" \"B\" \"A\")) (lambda (label-seq) (require-seq-equal label-seq (list #t #t #t))))" +
    		"))" +
    		"" +
    		"(define parameter-spec (make-parameter-list (list (make-indicator-classifier-parameters (list word-list label-list))" +
    		"                             (make-indicator-classifier-parameters (list label-list label-list)))))" +
    		"(define best-params (opt sequence-family parameter-spec training-data))" +
    		"(define sequence-model (sequence-family best-params))" +
    		"(define foo (get-best-value (sequence-model (list \"A\" \"C\" \"A\" \"B\"))))" +
    		"(define bar (get-best-value (sequence-model (list \"C\" \"B\" \"C\"))))" +
    		"(list foo bar)";

    Object value = runTest(program);
    Object expected = runTest("(list (list #t #t #t #f) (list #t #t #t))");
    assertEquals(expected, value);
  }

  public void testOpt4() {
    String program = "(define entities (make-dictionary \"A\" \"B\" \"C\" \"D\"))" +
        "(define labels (list #t #f))" +
        "" +
        "(define inner-prod-family (params) " +
        "  (lambda (entity1 entity2) " +
        "    (let ((var (amb labels))) " +
        "      (make-inner-product-classifier var #t " +
        "        (get-ith-parameter params (dictionary-lookup entity1 entities)) " +
        "        (get-ith-parameter params (dictionary-lookup entity2 entities))) " +
        "     var)))"  +
        "" +
        "(define require1 (lambda (x) (add-weight (not x) 0.0)))" +
        "" +
        "(define training-data (list (list (list \"A\" \"A\") (lambda (x) (require1 (= x #t)))) " +
        "                            (list (list \"A\" \"B\") (lambda (x) (require1 (= x #t)))) " +
        "                            (list (list \"A\" \"C\") (lambda (x) (require1 (= x #f)))) " +
        "                            (list (list \"D\" \"C\") (lambda (x) (require1 (= x #t)))) " +
        " ))" +
        "(define parameters (make-parameter-list (array-map (lambda (x) (make-vector-parameters 2)) (dictionary-to-array entities))))" +
        "(perturb-parameters parameters 1.0)" +
        "(define best-params (opt inner-prod-family parameters training-data))" + 
        "(define output (inner-prod-family best-params))" + 
        "(get-best-value (lifted-list (output \"A\" \"A\") (output \"B\" \"A\") (output \"B\" \"C\") (output \"D\" \"A\")))" ;
        
    Object value = runTest(program);
    Object expected = runTest("(lifted-list #t #t #f #f)");

    assertEquals(expected, value);
  }
  
  public void testOpt5() {
    String program = "(define entities (make-dictionary \"params\" \"A\" \"B\" \"C\" \"D\"))" +
        "(define labels (list #t #f))" +
        "" +
        "(define inner-prod-family (params) " +
        "  (lambda (entity1 entity2 entity3) " +
        "    (let ((var (amb labels))) " +
        "      (make-ranking-inner-product-classifier var #t " +
        "        (get-ith-parameter params (dictionary-lookup entity1 entities)) " +
        "        (get-ith-parameter params (dictionary-lookup entity2 entities))" +
        "        (get-ith-parameter params (dictionary-lookup entity3 entities))) " +
        "     var)))"  +
        "" +
        "(define require1 (lambda (x) (add-weight (not x) 0.0)))" +
        "" +
        "(define training-data (list (list (list \"params\" \"A\" \"D\") (lambda (x) (require1 (= x #t)))) " +
        "                            (list (list \"params\" \"A\" \"B\") (lambda (x) (require1 (= x #t)))) " +
        "                            (list (list \"params\" \"A\" \"C\") (lambda (x) (require1 (= x #t)))) " +
        "                            (list (list \"params\" \"B\" \"C\") (lambda (x) (require1 (= x #t)))) " +
        " ))" +
        "(define parameters (make-parameter-list (array-map (lambda (x) (make-vector-parameters 2)) (dictionary-to-array entities))))" +
        "(perturb-parameters parameters 0.1)" +
        "(define best-params (opt inner-prod-family parameters training-data))" + 
        "(define output (inner-prod-family best-params))" +
        "(get-best-value (lifted-list (output \"params\" \"A\" \"B\") (output \"params\" \"B\" \"D\") (output \"params\" \"C\" \"A\")))" ;

    Object value = runTest(program);
    Object expected = runTest("(lifted-list #t #t #f)");

    assertEquals(expected, value);
  }

  private Object runTest(String expressionString) {
    String wrappedExpressionString = "(begin " + expressionString + ")";
    return eval.eval(parser.parse(wrappedExpressionString)).getValue();
  }

  private String runTestString(String expressionString) {
    return (String) runTest(expressionString);
  }

  private int runTestInt(String expressionString) {
    return (Integer) runTest(expressionString);
  }

  private double runTestDouble(String expressionString) {
    return (Double) runTest(expressionString);
  }
}
