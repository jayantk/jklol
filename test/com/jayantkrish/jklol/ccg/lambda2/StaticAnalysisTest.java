package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.Scope;

public class StaticAnalysisTest extends TestCase {
  
  String[] expressionStrings = new String[] {
    "(foo bar baz)",
    "(lambda (foo) (foo bar baz))",
    "(lambda (foo) (foo bar (lambda (baz) (abcd)) baz))",
    "(lambda (foo) (foo bar (lambda (baz) (abcd baz))))",
    "(lambda (foo) (foo bar (lambda (baz) (abcd baz))))",
  };
  
  Expression2[] expressions = new Expression2[expressionStrings.length];

  TypeDeclaration typeDeclaration;

  public void setUp() {
    ExpressionParser<Expression2> parser = ExpressionParser.expression2();
    for (int i = 0; i < expressionStrings.length; i++) {
      expressions[i] = parser.parse(expressionStrings[i]);
    }
    Map<String, Type> typeReplacementMap = Maps.newHashMap();
    typeReplacementMap.put("<<e,t>,<<e,i>,e>>", Type.parseFrom("<<#1,t>,<<#1,i>,#1>>"));
    typeReplacementMap.put("<<e,t>,t>", Type.parseFrom("<<#1,t>,t>"));
    typeReplacementMap.put("<<e,t>,i>", Type.parseFrom("<<#1,t>,i>"));

    Map<String, String> subtypeMap = Maps.newHashMap();
    subtypeMap.put("lo", "e");
    subtypeMap.put("c", "lo");
    subtypeMap.put("co", "lo");
    subtypeMap.put("s", "lo");
    subtypeMap.put("r", "lo");
    subtypeMap.put("l", "lo");
    subtypeMap.put("m", "lo");
    subtypeMap.put("p", "lo");
    typeDeclaration = new ExplicitTypeDeclaration(subtypeMap, typeReplacementMap);
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
    scope = StaticAnalysis.getEnclosingScope(expressions[1], 4);
    assertEquals(Sets.newHashSet("foo"), scope.getBoundVariables());
    scope = StaticAnalysis.getEnclosingScope(expressions[1], 7);
    assertEquals(Sets.newHashSet("foo"), scope.getBoundVariables());
    assertEquals(1, scope.getStart());
    assertEquals(8, scope.getEnd());
    assertEquals(3, scope.getBindingIndex("foo"));

    scope = StaticAnalysis.getEnclosingScope(expressions[2], 6);
    assertEquals(Sets.newHashSet("foo"), scope.getBoundVariables());
    scope = StaticAnalysis.getEnclosingScope(expressions[2], 8);
    assertEquals(Sets.newHashSet("foo", "baz"), scope.getBoundVariables());
    scope = StaticAnalysis.getEnclosingScope(expressions[2], 11);
    assertEquals(Sets.newHashSet("foo", "baz"), scope.getBoundVariables());
    assertEquals(8, scope.getStart());
    assertEquals(13, scope.getEnd());
    assertEquals(3, scope.getBindingIndex("foo"));
    assertEquals(10, scope.getBindingIndex("baz"));

    scope = StaticAnalysis.getEnclosingScope(expressions[2], 13);
    assertEquals(Sets.newHashSet("foo"), scope.getBoundVariables());
  }

  public void testTypeInferenceConstant() {
    runTypeInferenceTest("foo:e", "e");
    runTypeInferenceTest("foo:<e,t>", "<e,t>");
  }
  
  public void testTypeInferenceReplacements() {
    runTypeInferenceTest("foo:lo", "lo");
    runTypeInferenceTest("foo:<lo,i>", "<lo,i>");
  }

  public void testTypeInferenceLambda() {
    runTypeInferenceTest("(lambda ($0) (foo:<e,t> $0))", "<e,t>");
    runTypeInferenceTest("(lambda ($0 $1) (and:<t,<t,t>> ($1 $0) (foo:<e,t> $0)))", "<e,<<e,t>,t>>");
  }
  
  public void testTypeInferenceLambda2() {
    runTypeInferenceTest("(lambda ($0 $1) (and:<t*,t> ($1 $0) (foo:<e,t> $0) (city:<c,t> $0)))", "<c,<<c,t>,t>>");
  }
  
  public void testTypeInferenceLambda3() {
    runTypeInferenceTest("(lambda ($0) (and:<t*,t> (city:<c,t> $0) (loc:<lo,<lo,t>> $0 alaska:s)))", "<c,t>");
  }
  
  public void testTypeInferenceLambda4() {
    runTypeInferenceTest("(argmax:<<e,t>,<<e,i>,e>> (lambda ($0) (and:<t*,t> (city:<c,t> $0) (loc:<lo,<lo,t>> $0 alaska:s))) (lambda ($0) (size:<lo,i> $0)))",
        "c");
  }
  
  public void testTypeInferenceLambda5() {
    runTypeInferenceTest("(size:<lo,i> (argmax:<<e,t>,<<e,i>,e>> (lambda ($0) (and:<t*,t> (city:<c,t> $0) (loc:<lo,<lo,t>> $0 alaska:s))) (lambda ($0) (size:<lo,i> $0))))",
        "i");
  }
  
  public void testTypeInferenceNestedLambda() {
    String expression = "(count:<<e,t>,i> (lambda ($0) (and:<t,<t,t>> (state:<e,t> $0)"
        + "(exists:<<e,t>,t> (lambda ($1) (and:<t,<t,t>> (city:<e,t> $1) (loc:<e,<e,t>> $1 $0)))))))";
    runTypeInferenceTest(expression, "i");
  }
  
  public void testTypeInferenceNestedLambdaFunction() {
    String expression = "(lambda ($0) (count:<<e,t>,i> $0))";
    runTypeInferenceTest(expression, "<<e,t>,i>");
  }
  
  public void testTypeInferenceNestedLambdaFunction2() {
    String expression = "(lambda ($0) (lambda ($1) (cause:<e,<e,t>> $0 ($1 foo:a))))";
    runTypeInferenceTest(expression, "<e,<<a,e>,t>>");
  }

  public void testRepeatedArguments() {
    runTypeInferenceTest("(lambda ($0 $1) (and:<t*,t> (bar:<e,t> $0) ($1 $0) (foo:<e,t> $0)))", "<e,<<e,t>,t>>");
  }

  public void testUnknownPropagation() {
    runTypeInferenceTest("(lambda ($0 $1) ($0 $1))", "<<⊤,⊤>,<⊤,⊤>>");
  }

  public void testMultipleArguments() {
    runTypeInferenceTest("(lambda ($f0) (argmax:<<e,t>,<<e,i>,e>> $f0 (lambda ($1) (size:<lo,i> $1))))",
        "<<lo,t>,lo>");
  }
  
  public void testMultipleArguments2() {
    runTypeInferenceTest("(lambda ($f0 $f1) (argmax:<<e,t>,<<e,i>,e>> $f0 $f1))",
        "<<e,t>,<<e,i>,e>>");
  }
  
  public void testMultipleArguments3() {
    runTypeInferenceTest("(argmax:<<e,t>,<<e,i>,e>> (lambda ($0) (city:<c,t> $0)) (lambda ($1) (size:<lo,i> $1)))",
        "c");
  }
  
  public void testMultipleArguments4() {
    runTypeInferenceTest("(lambda ($f0) (argmax:<<e,t>,<<e,i>,e>> (lambda ($0) ($f0 $0)) (lambda ($1) (size:<lo,i> $1))))",
        "<<lo,t>,lo>");
  }

  public void testSomething() {
    runTypeInferenceTest("(lambda ($f0) (named:<e,<n,t>> $f0 austin:n))", "<e,t>");
  }

  public void testSomething2() {
    runTypeInferenceTest("(lambda ($f0 $f1) (and:<t*,t> ($f0 $f1) (exists:<<e,t>,t> (lambda ($1) (and:<t*,t> (city:<c,t> $1) (named:<e,<n,t>> $1 austin:n) (loc:<lo,<lo,t>> $1 $f1))))))",
        "<<lo,t>,<lo,t>>");
  }

  public void testSomething3() {
    runTypeInferenceTest("(lambda ($0) (size:<lo,i> (argmax:<<e,t>,<<e,i>,e>> (lambda ($1) ($0 $1)) (lambda ($1) (size:<lo,i> $1)))))",
        "<<lo,t>,i>");
  }
  
  public void testSomething4() {
    Type expected = Type.parseFrom("<<lo,t>,<lo,<lo,t>>>");
    Expression2 exp = ExpressionParser.expression2().parse(
        "(lambda ($0) (lambda ($1) (lambda ($2) (and:<t*,t> ($0 $2) (loc:<lo,<lo,t>> $2 $1)))))");
    Type predicted = StaticAnalysis.inferType(exp, expected, typeDeclaration);
    assertEquals(expected, predicted);
  }
  
  public void testSomething5() {
    runTypeInferenceTest("(get-denotation-c:<<d,⊥>,<<<t,⊥>,<a,⊥>>,⊥>> (lambda ($0) (display:<⊤,⊥> $0)) (lambda ($0 $1) (animal-c:<<t,⊥>,<a,⊥>> (lambda ($2) (plant-c:<<t,⊥>,<a,⊥>> (lambda ($3) (and-c:<<t,⊥>,<t*,⊥>> $0 $2 $3)) $1)) $1)))",
        "⊥");
  }

  public void testSomething6() {
    runTypeInferenceTest("(get-denotation-c:<<d,⊥>,<<<t,⊥>,<a,⊥>>,⊥>> (lambda ($0) (display:<⊤,⊥> $0)) (lambda ($0 $1) (secondary-consumer-c:<<t,⊥>,<a,⊥>> (lambda ($2) (top-predator-c:<<t,⊥>,<a,⊥>> (lambda ($3) (and-c:<<t,⊥>,<t*,⊥>> $0 $2 $3)) $1)) $1)))",
        "⊥");
  }
  
  public void testSomething7() {
    runTypeInferenceTest("(lambda ($f0 $f1) (and:<t*,t> ($f0 $f1) (exists:<<e,t>,t> (lambda ($1) (loc:<lo,<lo,t>> $1 $f1)))))",
        "<<lo,t>,<lo,t>>");
  }

  public void testVariablesSameName() {
    runTypeInferenceTest("(lambda (f) (and:<t*,t> (f texas:e) ((lambda (f) (state:<e,t> f)) austin:e)))",
        "<<e,t>,t>");
  }
  
  public void testPolymorphism() {
    runTypeInferenceTest("(count:<<e,t>,i> (lambda ($0) (and:<t*,t> (city:<c,t> $0) (loc:<lo,<lo,t>> $0 louisiana:s))))",
        "i");
  }

  private void runTypeInferenceTest(String expression, String expectedType) {
    Type expected = Type.parseFrom(expectedType);
    Expression2 exp = ExpressionParser.expression2().parse(expression);
    TypeInference inference = StaticAnalysis.typeInference(exp, TypeDeclaration.TOP, typeDeclaration);
    System.out.println("original:");
    System.out.println(inference.getConstraints());
    System.out.println("solved:");
    System.out.println(inference.getSolvedConstraints());

    assertTrue(inference.getSolvedConstraints().isSolvable());
    assertEquals(expected, inference.getExpressionTypes().get(0));
  }
}
