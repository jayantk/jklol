package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;

public class ExpressionTest extends TestCase {

  ExpressionParser parser;
  
  public void setUp() {
    parser = new ExpressionParser();
  }

  public void testApplication() {
    ApplicationExpression application = (ApplicationExpression) parser.parseSingleExpression("(foo (a b) (c a))");
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
    LambdaExpression lf = (LambdaExpression) parser.parseSingleExpression("(lambda a c (foo (a b) (c a)))");
    
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
  
  public void testQuantifier() {
    QuantifierExpression qf = (QuantifierExpression) parser.parseSingleExpression("(exists a c (foo (a b) (c a)))");
    
    Expression body = parser.parseSingleExpression("(foo (a b) (c a))");
    List<Expression> arguments = parser.parse("a c");
    Set<Expression> freeVariables = Sets.newHashSet(parser.parse("foo b"));
    assertEquals(body, qf.getBody());
    assertEquals(arguments, qf.getBoundVariables());
    assertEquals(freeVariables, qf.getFreeVariables());
  }
  
  public void testSimplify() {
    Expression expression = parser.parseSingleExpression("(and (a b) ((lambda x (exists y (bar x y))) z))");
    Expression simplified = expression.simplify();
    
    Expression expected = parser.parseSingleExpression("(exists y (and (a b) (bar z y)))");
    assertEquals(expected, simplified);
  }
}
