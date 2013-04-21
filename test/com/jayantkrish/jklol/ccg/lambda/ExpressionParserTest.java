package com.jayantkrish.jklol.ccg.lambda;

import junit.framework.TestCase;

public class ExpressionParserTest extends TestCase {
  
  ExpressionParser parser;
  
  public void setUp() {
    parser = new ExpressionParser();
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
}
