package com.jayantkrish.jklol.ccg.lambda2;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

public class Expression2Test extends TestCase {
  
  String[] expressionStrings = new String[] {
      "(foo bar baz)",
      "(lambda foo (foo bar baz))",
      "(lambda foo (foo bar (lambda baz (abcd)) baz))",
      "(lambda foo (foo bar (lambda baz (abcd baz))))",
  };

  Expression2[] expressions = new Expression2[expressionStrings.length];

  public void setUp() {
    ExpressionParser<Expression2> parser = ExpressionParser.expression2();
    for (int i = 0; i < expressionStrings.length; i++) {
      expressions[i] = parser.parse(expressionStrings[i]);
    }
  }
  
  public void testGetChildIndexes() {
    assertArrayEquals(new int[] {1, 2, 3}, expressions[0].getChildIndexes(0));
    assertArrayEquals(new int[] {}, expressions[0].getChildIndexes(1));
    
    assertArrayEquals(new int[] {4, 5, 6, 11}, expressions[2].getChildIndexes(3));
  }
  
  public void testGetSubexpression() {
    assertEquals(expressions[0], expressions[0].getSubexpression(0));
    assertEquals(expressions[0], expressions[1].getSubexpression(3));
  }
  
  public void testFind() {
    assertEquals(0, expressions[0].find(expressions[0]));
    assertEquals(-1, expressions[0].find(expressions[1]));
    assertEquals(3, expressions[1].find(expressions[0]));
  }
  
  private void assertArrayEquals(int[] x, int[] y) {
    assertEquals(x.length, y.length);
    for (int i = 0; i < x.length; i++) {
      assertEquals(x[i], y[i]);
    }
  }

}
