package com.jayantkrish.jklol.ccg.lambda;

import junit.framework.TestCase;

import com.jayantkrish.jklol.lisp.SExpression;

public class ExpressionParserTest extends TestCase {
  
  ExpressionParser<Expression> parser;
  ExpressionParser<Expression> tlcParser;
  ExpressionParser<Expression> unequalQuoteParser;
  ExpressionParser<SExpression> lispParser;
  ExpressionParser<Type> typeParser;

  public void setUp() {
    parser = ExpressionParser.lambdaCalculus();
    tlcParser = ExpressionParser.typedLambdaCalculus();
    unequalQuoteParser = new ExpressionParser<Expression>('(', ')', '<', '>', true,
        ExpressionParser.DEFAULT_SEPARATOR, ExpressionFactories.getDefaultFactory());
    lispParser = ExpressionParser.sExpression();
    typeParser = ExpressionParser.typeParser();
  }

  public void testParseConstant() {
    Expression result = parser.parseSingleExpression("x");
    
    assertTrue(result instanceof ConstantExpression);    
    assertEquals("x", ((ConstantExpression) result).getName());
  }

  public void testParse() {
    Expression result = parser.parseSingleExpression("(and (/m/abc x) (/m/bcd y) /m/cde)");
    
    assertTrue(result instanceof CommutativeOperator);
    CommutativeOperator application = (CommutativeOperator) result;
    assertEquals("and", ((ConstantExpression) application.getOperatorName()).getName());
    assertEquals(3, application.getArguments().size());
    
    System.out.println(application);
  }

  public void testParseQuotes() {
    Expression result = parser.parseSingleExpression("(and \"(/m/abc x) (/m/bcd y)\" /m/cde)");
    
    assertTrue(result instanceof CommutativeOperator);
    CommutativeOperator application = (CommutativeOperator) result;
    assertEquals("and", ((ConstantExpression) application.getOperatorName()).getName());
    assertEquals(2, application.getArguments().size());
    
    System.out.println(application);
  }

  public void testParseIgnoreQuotes() {
    // Check that single quotes are ignored by this parser.
    Expression result = unequalQuoteParser.parseSingleExpression("(and \"(/m/abc x) (/m/bcd y)\" /m/cde)");
    
    assertTrue(result instanceof ApplicationExpression);
    ApplicationExpression application = (ApplicationExpression) result;
    assertEquals("and", ((ConstantExpression) application.getFunction()).getName());
    assertEquals(5, application.getArguments().size());
  }

  public void testParseUnequalQuotes() {
    // Check that the unequal quotes work properly.
    Expression result = unequalQuoteParser.parseSingleExpression("(and <(/m/abc x) (/m/bcd y)> /m/cde)");

    assertTrue(result instanceof ApplicationExpression);
    ApplicationExpression application = (ApplicationExpression) result;
    assertEquals("and", ((ConstantExpression) application.getFunction()).getName());
    assertEquals(2, application.getArguments().size());
    assertEquals("<(/m/abc x) (/m/bcd y)>", ((ConstantExpression) application.getArguments().get(0)).getName());
  }

  public void testEmptyExpression() {
    SExpression result = lispParser.parseSingleExpression("()");
    assertNull(result.getConstant());
    assertEquals(0, result.getSubexpressions().size());
  }

  public void testParseAtomicType() {
    Type result = typeParser.parseSingleExpression("e");
    assertTrue(result.isAtomic());
    assertEquals("e", result.getAtomicTypeName());
  }

  public void testParseFunctionalType() {
    Type result = typeParser.parseSingleExpression("<e,<<e,t>,t>>");
    assertTrue(result.isFunctional());
    assertFalse(result.isAtomic());
    assertEquals("e", result.getArgumentType().getAtomicTypeName());
    assertEquals("t", result.getReturnType().getReturnType().getAtomicTypeName());
    assertEquals("<e,t>", result.getReturnType().getArgumentType().toString());
  }
  
  public void testTypedLambdaCalculus() {
    ConstantExpression result = (ConstantExpression) tlcParser.parseSingleExpression("x:e");
    
    assertEquals("x", result.getName());
    assertEquals("e", result.getType(TypeContext.empty()).getAtomicTypeName());
  }
  
  public void testTypedLambdaCalculus2() {
    ConstantExpression result = (ConstantExpression) tlcParser.parseSingleExpression("f:<e,t>");

    assertEquals("f", result.getName());
    assertEquals("e", result.getType(TypeContext.empty()).getArgumentType().getAtomicTypeName());
    assertEquals("t", result.getType(TypeContext.empty()).getReturnType().getAtomicTypeName());
  }

  public void testTypedLambdaCalculus3() {
    LambdaExpression result = (LambdaExpression) tlcParser.parseSingleExpression("(lambda f:<e,t> x:e (f x))");

    assertEquals("<<e,t>,<e,t>>", result.getType(TypeContext.empty()).toString());
    assertEquals("(f x)", result.getBody().toString());
  }
}
