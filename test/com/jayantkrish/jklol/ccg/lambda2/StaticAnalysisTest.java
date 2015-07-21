package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.Scope;

public class StaticAnalysisTest extends TestCase {
  
  String[] expressionStrings = new String[] {
    "(foo bar baz)",
    "(lambda foo (foo bar baz))",
    "(lambda foo (foo bar (lambda baz (abcd)) baz))",
    "(lambda foo (foo bar (lambda baz (abcd baz))))",
  };
  
  Expression2[] expressions = new Expression2[expressionStrings.length];
  
  Map<String, String> typeReplacementMap;

  public void setUp() {
    ExpressionParser<Expression2> parser = ExpressionParser.expression2();
    for (int i = 0; i < expressionStrings.length; i++) {
      expressions[i] = parser.parseSingleExpression(expressionStrings[i]);
    }
    
    typeReplacementMap = Maps.newHashMap();
    typeReplacementMap.put("lo", "e");
  }

  public void testFreeVariables() {
    Set<String> freeVars = StaticAnalysis.getFreeVariables(expressions[0]);
    Set<String> expected = Sets.newHashSet("foo", "bar", "baz");
    assertEquals(expected, freeVars);

    freeVars = StaticAnalysis.getFreeVariables(expressions[1]);
    expected = Sets.newHashSet("bar", "baz");
    assertEquals(expected, freeVars);

    freeVars = StaticAnalysis.getFreeVariables(expressions[2]);
    expected = Sets.newHashSet("bar", "baz", "abcd");
    assertEquals(expected, freeVars);

    freeVars = StaticAnalysis.getFreeVariables(expressions[3]);
    expected = Sets.newHashSet("bar", "abcd");
    assertEquals(expected, freeVars);
  }
  
  public void testGetEnclosingScope() {
    Scope scope = StaticAnalysis.getEnclosingScope(expressions[0], 1);
    
    assertEquals(Collections.emptySet(), scope.getBoundVariables());
    
    scope = StaticAnalysis.getEnclosingScope(expressions[1], 0);
    assertEquals(Collections.emptySet(), scope.getBoundVariables());
    
    scope = StaticAnalysis.getEnclosingScope(expressions[1], 1);
    assertEquals(Sets.newHashSet("foo"), scope.getBoundVariables());
    scope = StaticAnalysis.getEnclosingScope(expressions[1], 3);
    assertEquals(Sets.newHashSet("foo"), scope.getBoundVariables());
    scope = StaticAnalysis.getEnclosingScope(expressions[1], 6);
    assertEquals(Sets.newHashSet("foo"), scope.getBoundVariables());
    assertEquals(1, scope.getStart());
    assertEquals(7, scope.getEnd());

    scope = StaticAnalysis.getEnclosingScope(expressions[2], 5);
    assertEquals(Sets.newHashSet("foo"), scope.getBoundVariables());
    scope = StaticAnalysis.getEnclosingScope(expressions[2], 7);
    assertEquals(Sets.newHashSet("foo", "baz"), scope.getBoundVariables());
    scope = StaticAnalysis.getEnclosingScope(expressions[2], 9);
    assertEquals(Sets.newHashSet("foo", "baz"), scope.getBoundVariables());
    assertEquals(7, scope.getStart());
    assertEquals(11, scope.getEnd());

    scope = StaticAnalysis.getEnclosingScope(expressions[2], 11);
    assertEquals(Sets.newHashSet("foo"), scope.getBoundVariables());
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
  
  public void testSomething3() {
    runTypeInferenceTest("(lambda $0 (size:<lo,i> (argmax:<<e,t>,<<e,i>,e>> (lambda $1 ($0 $1)) (lambda $1 (size:<lo,i> $1)))))",
        "<<e,t>,i>");
  }
  
  public void testSomething4() {
    Type expected = Type.parseFrom("<<e,t>,<e,<e,t>>>");
    Expression2 exp = ExpressionParser.expression2().parseSingleExpression(
        "(lambda $0 (lambda $1 (lambda $2 (and:<t*,t> ($0 $2) (loc:<lo,<lo,t>> $2 $1)))))");
    Type predicted = StaticAnalysis.inferType(exp, expected, typeReplacementMap);
    assertEquals(expected, predicted);
  }

  public void testVariablesSameName() {
    runTypeInferenceTest("(lambda f (and:<t*,t> (f texas:e) ((lambda f (state:<e,t> f)) austin:e)))",
        "<<e,t>,t>");
  }

  private void runTypeInferenceTest(String expression, String expectedType) {
    Type expected = Type.parseFrom(expectedType);
    Expression2 exp = ExpressionParser.expression2().parseSingleExpression(expression);
    Type predicted = StaticAnalysis.inferType(exp, typeReplacementMap);
    assertEquals(expected, predicted);
  }
}
