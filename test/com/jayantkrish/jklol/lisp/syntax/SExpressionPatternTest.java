package com.jayantkrish.jklol.lisp.syntax;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.syntax.SExpressionPatternMatcher.Match;
import com.jayantkrish.jklol.lisp.syntax.SExpressionPatternMatcher.SExpressionPattern;
import com.jayantkrish.jklol.util.IndexedList;

public class SExpressionPatternTest extends TestCase {

  String[] expressionStrings = new String[] {
      "(foo bar foo)",
      "(foo)"
  };
  
  SExpression[] expressions = new SExpression[expressionStrings.length];
  
  public void setUp() {
    for (int i = 0; i < expressionStrings.length; i++) {
      expressions[i] = ExpressionParser.sExpression(IndexedList.<String>create())
          .parseSingleExpression(expressionStrings[i]);
    }
  }

  public void testMatchConstant() {
    SExpressionPattern pattern = new SExpressionPatternMatcher.ConstantSExpressionPattern("foo");

    Match match1 = pattern.getNextMatch(expressions[0], 0);
    Match match2 = pattern.getNextMatch(expressions[0], match1.getSpanStart() + 1);
    Match match3 = pattern.getNextMatch(expressions[0], match2.getSpanStart() + 1);

    assertEquals(1, match1.getSpanStart());
    assertEquals(2, match1.getSpanEnd());
    assertEquals(3, match2.getSpanStart());
    assertEquals(4, match2.getSpanEnd());
    assertNull(match3);
  }
  
  public void testMatchSequence() {
    SExpressionPattern c1 = new SExpressionPatternMatcher.ConstantSExpressionPattern("foo");
    SExpressionPattern c2= new SExpressionPatternMatcher.ConstantSExpressionPattern("bar");
    SExpressionPattern pattern = new SExpressionPatternMatcher.ListPattern(c1, c2);
    
    Match match1 = pattern.getNextMatch(expressions[0], 0);
    Match match2 = pattern.getNextMatch(expressions[0], match1.getSpanStart() + 1);
    
    assertEquals(1, match1.getSpanStart());
    assertEquals(3, match1.getSpanEnd());
    assertNull(match2);
  }
  
  public void testMatchSubexpression1() {
    SExpressionPattern pattern = new SExpressionPatternMatcher.SubexpressionPattern();

    Match match1 = pattern.getNextMatch(expressions[0], 0);
    Match match2 = pattern.getNextMatch(expressions[0], match1.getSpanStart() + 1);

    System.out.println(match1);
    System.out.println(match2);
  }
  
  public void testMatchSubexpression2() {
    SExpressionPattern c1 = new SExpressionPatternMatcher.ConstantSExpressionPattern("foo");
    SExpressionPattern pattern = new SExpressionPatternMatcher.SubexpressionPattern(c1);

    Match match1 = pattern.getNextMatch(expressions[0], 0);
    assertNull(match1);
    
    Match match2 = pattern.getNextMatch(expressions[1], 0);
    assertEquals(0, match2.getSpanStart());
    assertEquals(2, match2.getSpanEnd());
  }
}
