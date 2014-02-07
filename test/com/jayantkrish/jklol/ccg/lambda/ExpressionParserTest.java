package com.jayantkrish.jklol.ccg.lambda;

import junit.framework.TestCase;

import com.jayantkrish.jklol.lisp.SExpression;

public class ExpressionParserTest extends TestCase {
  
  ExpressionParser<Expression> parser;
  ExpressionParser<Expression> unequalQuoteParser;
  ExpressionParser<SExpression> lispParser;
  
  public void setUp() {
    parser = ExpressionParser.lambdaCalculus();
    unequalQuoteParser = new ExpressionParser<Expression>('(', ')', '<', '>',
        ExpressionFactories.getDefaultFactory());
    lispParser = ExpressionParser.sExpression();
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
}
