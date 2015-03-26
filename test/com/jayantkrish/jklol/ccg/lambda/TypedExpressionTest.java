package com.jayantkrish.jklol.ccg.lambda;

import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Maps;

public class TypedExpressionTest extends TestCase {

  Map<String, String> typeReplacementMap;
  
  public void setUp() {
    typeReplacementMap = Maps.newHashMap();
    typeReplacementMap.put("lo", "e");
  }
  
  public void testTypeInferenceConstant() {
    runTypeInferenceTest("foo:e", "e");
    runTypeInferenceTest("foo:<e,t>", "<e,t>");
  }
  
  public void testTypeInferenceReplacements() {
    runTypeInferenceTest("foo:lo", "e");
    runTypeInferenceTest("foo:<lo,i>", "<e,i>");
  }

  public void testTypeInferenceLambda() {
    runTypeInferenceTest("(lambda $0 (foo:<e,t> $0))", "<e,t>");
    runTypeInferenceTest("(lambda $0 $1 (and:<t,<t,t>> ($1 $0) (foo:<e,t> $0)))", "<e,<<e,t>,t>>");
  }
  
  public void testTypeInferenceNestedLambda() {
    String expression = "(count:<<e,t>,i> (lambda $0 (and:<t,<t,t>> (state:<e,t> $0)"
        + "(exists:<<e,t>,t> (lambda $1 (and:<t,<t,t>> (city:<e,t> $1) (loc:<e,<e,t>> $1 $0)))))))";
    runTypeInferenceTest(expression, "i");
  }
  
  public void testRepeatedArguments() {
    runTypeInferenceTest("(lambda $0 $1 (and:<t*,t> (bar:<e,t> $0) ($1 $0) (foo:<e,t> $0)))", "<e,<<e,t>,t>>");
  }

  public void testUnknownPropagation() {
    runTypeInferenceTest("(lambda $0 $1 ($0 $1))", "<<unknown,unknown>,<unknown,unknown>>");
  }
  
  public void testMultipleArguments() {
    runTypeInferenceTest("(lambda $f0 (argmax:<<e,t>,<<e,i>,e>> $f0 (lambda $1 (size:<lo,i> $1))))",
        "<<e,t>,e>");
  }
  
  public void testSomething() {
    runTypeInferenceTest("(lambda $f0 (named:<e,<n,t>> $f0 austin:n))", "<e,t>");
  }
  
  public void testSomething2() {
    runTypeInferenceTest("(lambda $f0 $f1 (and:<t*,t> ($f0 $f1) (exists:<<e,t>,t> (lambda $1 (and:<t*,t> (city:<c,t> $1) (named:<e,<n,t>> $1 austin:n) (loc:<lo,<lo,t>> $1 $f1))))))",
        "<<e,t>,<e,t>>");
  }

  private void runTypeInferenceTest(String expression, String expectedType) {
    Type expected = Type.parseFrom(expectedType);
    Expression exp = ExpressionParser.lambdaCalculus().parseSingleExpression(expression);
    Type predicted = TypedExpression.inferType(exp, typeReplacementMap);
    assertEquals(expected, predicted);
  }
}
