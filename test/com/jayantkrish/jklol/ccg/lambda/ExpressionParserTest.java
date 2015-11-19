package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IndexedList;

public class ExpressionParserTest extends TestCase {
  
  ExpressionParser<Expression2> parser;
  ExpressionParser<Expression2> unequalQuoteParser;
  ExpressionParser<SExpression> lispParser;
  ExpressionParser<Type> typeParser;
  
  public void setUp() {
    parser = ExpressionParser.expression2();
    unequalQuoteParser = new ExpressionParser<Expression2>('(', ')', '<', '>', '\\', true,
        ExpressionParser.DEFAULT_SEPARATOR, new String[0], new String[0],
        ExpressionFactories.getExpression2Factory());
    lispParser = ExpressionParser.sExpression(IndexedList.<String>create());
    typeParser = ExpressionParser.typeParser();
  }

  public void testParseConstant() {
    Expression2 result = parser.parseSingleExpression("x");
    
    assertTrue(result.isConstant());    
    assertEquals("x", result.getConstant());
  }

  public void testParse() {
    Expression2 result = parser.parseSingleExpression("(and (/m/abc x) (/m/bcd y) /m/cde)");
    
    assertFalse(result.isConstant());
    List<Expression2> subexpressions = result.getSubexpressions();
    assertEquals("and", subexpressions.get(0).getConstant());
    assertEquals(4, subexpressions.size());
  }

  public void testParseQuotes() {
    Expression2 result = parser.parseSingleExpression("(and \"(/m/abc x) (/m/bcd y)\" /m/cde)");
    
    assertFalse(result.isConstant());
    List<Expression2> subexpressions = result.getSubexpressions();
    assertEquals("and", subexpressions.get(0).getConstant());
    assertEquals(3, subexpressions.size());
  }
  
  public void testParseQuotesEscape() {
    Expression2 result = parser.parseSingleExpression("(and \"(/m/abc x) \\\" (/m/bcd y)\" /m/cde)");
    
    assertFalse(result.isConstant());
    List<Expression2> subexpressions = result.getSubexpressions();
    assertEquals("and", subexpressions.get(0).getConstant());
    assertEquals("\"(/m/abc x) \\\" (/m/bcd y)\"", subexpressions.get(1).getConstant());
    assertEquals(3, subexpressions.size());
  }


  public void testParseIgnoreQuotes() {
    // Check that single quotes are ignored by this parser.
    Expression2 result = unequalQuoteParser.parseSingleExpression("(and \"(/m/abc x) (/m/bcd y)\" /m/cde)");
    
    assertFalse(result.isConstant());
    List<Expression2> subexpressions = result.getSubexpressions();
    assertEquals("and", subexpressions.get(0).getConstant());
    assertEquals(6, subexpressions.size());
  }

  public void testParseUnequalQuotes() {
    // Check that the unequal quotes work properly.
    Expression2 result = unequalQuoteParser.parseSingleExpression("(and <(/m/abc x) (/m/bcd y)> /m/cde)");

    List<Expression2> subexpressions = result.getSubexpressions();
    assertEquals("and", subexpressions.get(0).getConstant());
    assertEquals(3, subexpressions.size());
    assertEquals("<(/m/abc x) (/m/bcd y)>", subexpressions.get(1).getConstant());
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

  public void testParseRepeatedFunctionalType() {
    Type result = typeParser.parseSingleExpression("<e*,<<e,t>,t>>");
    assertTrue(result.isFunctional());
    assertFalse(result.isAtomic());
    assertEquals("e", result.getArgumentType().getAtomicTypeName());
    assertEquals("t", result.getReturnType().getReturnType().getAtomicTypeName());
    assertEquals("<e,t>", result.getReturnType().getArgumentType().toString());
  }
}
