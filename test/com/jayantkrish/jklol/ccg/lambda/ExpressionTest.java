package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;

public class ExpressionTest extends TestCase {

  ExpressionParser<Expression> parser;
  
  ApplicationExpression application;
  CommutativeOperator op;
  LambdaExpression lf;
  QuantifierExpression exists;
  
  public void setUp() {
    parser = ExpressionParser.lambdaCalculus();
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
  
  public void testSimplifyForAll() {
    Expression expression = parser.parseSingleExpression("(forall (a (set b ((lambda x x) d))) (and (exists c b (b c)) b))");
    Expression simplified = expression.simplify();
    
    Expression expected = parser.parseSingleExpression("(forall (a (set b d)) (exists c x (and (x c) b)))");
    assertTrue(expected.functionallyEquals(simplified));
  }
  
  public void testSimplifyForAllExists() {
    Expression expression = parser.parseSingleExpression("(exists b c (forall (b (set d e)) (and (b c))))");
    Expression simplified = expression.simplify();

    Expression expected = parser.parseSingleExpression("(forall (new_b (set d e)) (exists b c (and (new_b c))))");
    assertTrue(expected.functionallyEquals(simplified));
  }

  public void testSimplifyConjunction() {
    Expression expression = parser.parseSingleExpression("(exists a b (and (exists c (and c (lambda x x))) b))");
    Expression expected = parser.parseSingleExpression("(exists a b c (and c (lambda x x) b))");
    
    assertTrue(expected.functionallyEquals(expression.simplify()));
  }
  
  public void testExpandForAll() {
    ForAllExpression expression = (ForAllExpression) parser.parseSingleExpression("(forall (b (set d e)) (exists g c (and (b c))))");
    Expression expected = parser.parseSingleExpression("(and (exists g c (and (d c))) (exists g c (and (e c))))");
    
    assertTrue(expected.functionallyEquals(expression.expandQuantifier()));
  }
  
  public void testExpandForAll2() {
    ForAllExpression expression = (ForAllExpression) parser.parseSingleExpression("(forall ($pred (set (lambda $z (exists $y (and (/m/0c7zf $z) (/m/03__y $y) (/location/location/contains $y $z)))) /m/0357_)) (exists $x ($pred $x)))");
    Expression result = expression.expandQuantifier().simplify();
    
    Expression expected = parser.parseSingleExpression("(exists A B C (and (/m/0c7zf C) (/m/03__y B) (/location/location/contains B C) (/m/0357_ A)))");
    assertTrue(expected.functionallyEquals(result));
  }
  
  public void testExpandForAll3() {
    ForAllExpression expression = (ForAllExpression) parser.parseSingleExpression(
        "(forall (b (set d e)) (f (set x b)) (b f))");

    Expression expected = parser.parseSingleExpression("(and (d x) (d b) (e x) (e b))");
    assertTrue(expected.functionallyEquals(expression.expandQuantifier().simplify()));
  }
  
  public void testExpandForAll4() {
    ForAllExpression expression = (ForAllExpression) parser.parseSingleExpression(
        "(forall (b (set d e)) (f (set x y)) (exists c (and (b f) (b c))))");

    Expression expected = parser.parseSingleExpression("(exists h i j k (and (d x) (d h) (d y) (d i) (e x) (e j) (e y) (e k)))");
    assertTrue(expected.functionallyEquals(expression.expandQuantifier().simplify()));
  }
  
  public void testExpandForAll5() {
    ForAllExpression expression = (ForAllExpression) parser.parseSingleExpression(
        "(forall (b (set d (lambda a (forall (x (set p q)) (x a))))) (exists d (b d)))");    
    Expression expected = parser.parseSingleExpression("(exists x y z (and (d x) (p y) (q z)))");
    assertTrue(expected.functionallyEquals(expression.expandQuantifier().simplify()));
  }
  
  public void testExpandForAll6() {
    // The goal of this test is to ensure that the quantifiers in
    // this expression expand without crashing or producing an
    // exponential blow-up (causing the program to hang).
    String expressionString = "(forall (b (set (lambda f (forall (b (set (lambda e (mention e \"chad hurley\" concept:person)) (lambda e (mention e \"steve chen\" concept:person)))) (b f))) (lambda e (mention e \"jawed karim\" concept:person)))) (exists h c d g a (and (mention a \"youtube\" concept:company) (concept:company d) (equals c d) (mention g \"google\" concept:company) (concept:acquired g c) (equals d a) (b h) (concept:organizationhiredperson a h))))";
    ForAllExpression expression = (ForAllExpression) parser.parseSingleExpression(expressionString);
    expression.simplify();
  }

  /**
   * Verify that forall quantifier expansion doesn't cause an
   * exponential blow-up in expression size.
   */
  public void testExpandForAll7() {
    String expressionString = "(exists var246019 (forall (pred (set (lambda var970051 (forall (pred (set (lambda var932563 (forall (pred (set (lambda var872754 (forall (pred (set unknown unknown)) (pred var872754))) unknown)) (pred var932563))) (lambda x (mention x \"spain\" concept:musicartist)))) (pred var970051))) (lambda var586411 (forall (pred (set (lambda var784596 (forall (pred (set (lambda x (mention x \"italy\" concept:city)) unknown)) (pred var784596))) unknown)) (pred var586411))))) (pred var246019)))";

    Expression expression = parser.parseSingleExpression(expressionString).simplify();
    expression = expression.simplify();

    if (expression instanceof ForAllExpression) {
      expression = ((ForAllExpression) expression).expandQuantifier().simplify();
    }
    
    String expectedString = "(exists var126514 var294420 var437854 var507881 var810806 var87387 var999153 (and (unknown var294420) (unknown var437854) (unknown var810806) (mention var999153 \"spain\" concept:musicartist) (mention var507881 \"italy\" concept:city) (unknown var87387) (unknown var126514)))";
    Expression expected = parser.parseSingleExpression(expectedString).simplify();

    assertTrue(expected.functionallyEquals(expression));
  }
  
  public void testExpandForAll8() {
    // The goal of this test is to ensure that the quantifiers in
    // this expression expand without crashing or producing an
    // exponential blow-up (causing the program to hang).
    String expressionString = "(exists var404023 var611108 (forall (var233536 (set (lambda var560038 (mention var560038 \"austria\" concept:country)) (lambda var560038 (mention var560038 \"switzerland\" concept:country)))) (var492872 (set (lambda var411041 (forall (var492872 (set (lambda var657553 (forall (var492872 (set (lambda var475227 (mention var475227 \"european union\" concept:location)) (lambda var397358 (forall (var492872 (set (lambda var475227 (mention var475227 \"herzegovina\" concept:location)) (lambda var475227 (mention var475227 \"bosnia\" concept:location)))) (var492872 var397358))))) (var492872 var657553))) (lambda var475227 (mention var475227 \"belgium\" concept:city)))) (var492872 var411041))) (lambda var475227 (mention var475227 \"germany\" concept:city)))) (exists var826985 y (and (var233536 y) (var492872 var826985) (var404023 var611108) (concept:locationlocatedwithinlocation var826985 var611108) (concept:eventatlocation var611108 y)))))";

    Expression expression = parser.parseSingleExpression(expressionString).simplify();
    expression = expression.simplify();
    
    if (expression instanceof ForAllExpression) {
      expression = ((ForAllExpression) expression).expandQuantifier().simplify();
    }
  }
  
  public void testExpandForAll9() {
    String expressionString = "(exists a b ((lambda input ((lambda predicate (lambda x (forall (var2323 (set (lambda var560038 (mention var560038 \"austria\" concept:country)) (lambda var560038 (mention var560038 \"germany\" concept:country))))  (exists y (and (predicate x) (citylocatedincountry x y) (var2323 y)))))) ((lambda predicate2 (lambda x  (exists y (forall (var2323 (set (lambda var560038 (mention var560038 \"foo\" concept:country)) (lambda var560038 (mention var560038 \"bar\" concept:country))))  (and (predicate2 x) (headquarteredin x y) (var2323 y)))))) input))) a b))";

    Expression expression = parser.parseSingleExpression(expressionString).simplify();
    expression = expression.simplify();

    System.out.println(expression);
    if (expression instanceof ForAllExpression) {
      expression = ((ForAllExpression) expression).expandQuantifier().simplify();
    }
    
    System.out.println(expression);
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
