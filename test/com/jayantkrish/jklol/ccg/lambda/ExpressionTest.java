package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;

public class ExpressionTest extends TestCase {

  ExpressionParser parser;
  
  ApplicationExpression application;
  CommutativeOperator op;
  LambdaExpression lf;
  QuantifierExpression exists;
  
  public void setUp() {
    parser = new ExpressionParser();
    application = (ApplicationExpression) parser.parseSingleExpression("(foo (a b) (c a))");
    op = (CommutativeOperator) parser.parseSingleExpression("(and (a b) (c a) ((foo) d))");
    lf = (LambdaExpression) parser.parseSingleExpression("(lambda a c (foo (a b) (c a)))");
    exists = (QuantifierExpression) parser.parseSingleExpression("(exists a c (foo (a b) (c a)))");
  }
  
  public void testApplication() {
    Expression function = parser.parseSingleExpression("foo");
    List<Expression> arguments = parser.parse("(a b) (c a)");
    Set<Expression> freeVariables = Sets.newHashSet(parser.parse("foo a b c"));
    
    assertEquals(arguments, application.getArguments());
    assertEquals(function, application.getFunction());
    assertEquals(freeVariables, application.getFreeVariables());
    
    ApplicationExpression result = (ApplicationExpression) application.substitute(
        new ConstantExpression("a"), parser.parseSingleExpression("(d f)")).substitute(
            new ConstantExpression("foo"), parser.parseSingleExpression("(bar baz)"));
    
    function = parser.parseSingleExpression("(bar baz)");
    arguments = parser.parse("((d f) b) (c (d f))");
    freeVariables = Sets.newHashSet(parser.parse("bar baz d f b c"));
    
    assertEquals(arguments, result.getArguments());
    assertEquals(function, result.getFunction());
    assertEquals(freeVariables, result.getFreeVariables());
  }
  
  public void testLambda() {
    Expression body = parser.parseSingleExpression("(foo (a b) (c a))");
    List<Expression> arguments = parser.parse("a c");
    Set<Expression> freeVariables = Sets.newHashSet(parser.parse("foo b"));
    assertEquals(body, lf.getBody());
    assertEquals(arguments, lf.getArguments());
    assertEquals(freeVariables, lf.getFreeVariables());
    
    // Only free variables should get substituted.
    LambdaExpression result = (LambdaExpression) lf.substitute(new ConstantExpression("a"), 
        parser.parseSingleExpression("(d e)")).substitute(new ConstantExpression("b"), 
            parser.parseSingleExpression("(f g)"));

    body = parser.parseSingleExpression("(foo (a (f g)) (c a))");
    arguments = parser.parse("a c");
    freeVariables = Sets.newHashSet(parser.parse("foo f g"));
    assertEquals(body, result.getBody());
    assertEquals(arguments, result.getArguments());
    assertEquals(freeVariables, result.getFreeVariables());
  }
  
  public void testLambdaReduction() {
    LambdaExpression lf = (LambdaExpression) parser.parseSingleExpression("(lambda a c (exists d e (foo (a b e) (c a))))");
    
    List<Expression> arguments = parser.parse("(e f) (lambda g g)");
    Expression result = lf.reduce(arguments);
    Expression expected = parser.parseSingleExpression("(exists d x (foo ((e f) b x) ((lambda g g) (e f))))");
    assertTrue(expected.functionallyEquals(result));
  }
  
  public void testLambdaReduction2() {
    LambdaExpression lf = (LambdaExpression) parser.parseSingleExpression("(lambda a c (lambda d e (foo (a b e) (c a))))");
    
    List<Expression> arguments = parser.parse("(e f) (lambda d d)");
    Expression result = lf.reduce(arguments);
    Expression expected = parser.parseSingleExpression("(lambda d x (foo ((e f) b x) ((lambda d d) (e f))))");
    assertTrue(expected.functionallyEquals(result));
  }
  
  public void testLambdaReduction3() {
    LambdaExpression lf = (LambdaExpression) parser.parseSingleExpression("(lambda a c (lambda d e (foo (a b e) (c a))))");

    List<Expression> arguments = parser.parse("a c");
    List<Expression> values = parser.parse("(e f) (lambda d d)");
    Expression result = lf.reduceArgument((ConstantExpression) arguments.get(1), values.get(1));
    Expression expected = parser.parseSingleExpression("(lambda a (lambda d e (foo (a b e) ((lambda d d) a))))");
    assertEquals(expected, result);
    
    result = lf.reduceArgument((ConstantExpression) arguments.get(0), values.get(0));
    expected = parser.parseSingleExpression("(lambda c (lambda d x (foo ((e f) b x) (c (e f)))))");
    assertTrue(expected.functionallyEquals(result));
  }

  public void testLambdaReduction4() {
    LambdaExpression lf = (LambdaExpression) parser.parseSingleExpression("(lambda a c (forall (d (set e a)) (foo (d b) a c)))");

    List<Expression> arguments = parser.parse("(e f) (lambda f (d f))");
    Expression result = lf.reduce(arguments);
    Expression expected = parser.parseSingleExpression("(forall (x (set e (e f))) (foo (x b) (e f) (lambda f (d f))))");

    assertTrue(expected.functionallyEquals(result));
  }

  public void testQuantifier() {
    QuantifierExpression qf = (QuantifierExpression) parser.parseSingleExpression("(exists a c (foo (a b) (c a)))");
    
    Expression body = parser.parseSingleExpression("(foo (a b) (c a))");
    Set<Expression> arguments = Sets.newHashSet(parser.parse("a c"));
    Set<Expression> freeVariables = Sets.newHashSet(parser.parse("foo b"));
    assertEquals(body, qf.getBody());
    assertEquals(arguments, qf.getBoundVariables());
    assertEquals(freeVariables, qf.getFreeVariables());
  }
  
  public void testSimplify() {
    Expression expression = parser.parseSingleExpression("(and (a b) ((lambda x (exists y (bar x y))) z))");
    Expression simplified = expression.simplify();
    
    Expression expected = parser.parseSingleExpression("(exists y (and (a b) (bar z y)))");
    assertTrue(expected.functionallyEquals(simplified));
  }
  
  public void testSimplifyQuantifier() {
    Expression expression = parser.parseSingleExpression("(exists a b (and (exists c b (b c)) b))");
    Expression simplified = expression.simplify();
    
    Expression expected = parser.parseSingleExpression("(exists a b c d (and (d c) b))");
    assertTrue(expected.functionallyEquals(simplified));
  }
  
  public void testFunctionallyEquals() {
    assertTrue(application.functionallyEquals(application));
  }
  
  public void testFunctionallyEqualsCommutative() {
    Expression expression = parser.parseSingleExpression("(and (c a) ((foo) d) (a b))");
    assertTrue(expression.functionallyEquals(op));
    assertTrue(op.functionallyEquals(expression));
  }
  
  public void testFunctionallyEqualsLambda() {
    Expression expression = parser.parseSingleExpression("(lambda d g (foo (d b) (g d)))");
    assertTrue(expression.functionallyEquals(lf));
    assertTrue(lf.functionallyEquals(expression));
  }
  
  public void testFunctionallyEqualsExists() {
    Expression expression = parser.parseSingleExpression("(exists g d (foo (g b) (d g)))");
    assertTrue(expression.functionallyEquals(exists));
    assertTrue(exists.functionallyEquals(expression));
  }
}
