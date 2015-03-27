package com.jayantkrish.jklol.lisp.syntax;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.lisp.SExpression;

public class SExpressionPatternMatcher {

  
  public static interface SExpressionPattern {
    
    public Match getNextMatch(SExpression expression, int startIndex);
  }
  
  public static class Match {
    private final int spanStart;
    private final int spanEnd;
    
    public Match(int spanStart, int spanEnd) {
      this.spanStart = spanStart;
      this.spanEnd = spanEnd;
    }

    public int getSpanStart() {
      return spanStart;
    }

    public int getSpanEnd() {
      return spanEnd;
    }
    
    @Override
    public String toString() {
      return "(" + spanStart + "," + spanEnd + ")";
    }
  }

  public static class ConstantSExpressionPattern implements SExpressionPattern {
    private String constantName;

    public ConstantSExpressionPattern(String constantName) {
      this.constantName = Preconditions.checkNotNull(constantName);
    }

    public Match getNextMatch(SExpression expression, int startIndex) {
      while (startIndex < expression.size() && !matches(expression.getSubexpression(startIndex)))  {
        startIndex++;
      }
      
      if (startIndex >= expression.size()) {
        return null;
      } else {
        return new Match(startIndex, startIndex + 1);
      }
    }

    public boolean matches(SExpression subexpression) {
      return subexpression.isConstant() && subexpression.getConstant().equals(constantName);
    }
  }

  public static class SubexpressionPattern implements SExpressionPattern {
    private final SExpressionPattern insidePattern;
    
    // Matches any subexpression.
    public SubexpressionPattern() {
      insidePattern = null;
    }
    
    public SubexpressionPattern(SExpressionPattern insidePattern) {
      this.insidePattern = insidePattern;
    }

    public Match getNextMatch(SExpression expression, int startIndex) {
      while (startIndex < expression.size()) {
        SExpression subexpression = expression.getSubexpression(startIndex);
        if (insidePattern == null) {
          return new Match(startIndex, startIndex + subexpression.size());
        } else {
          Match insideMatch = insidePattern.getNextMatch(subexpression, 1);
          if (insideMatch != null && insideMatch.getSpanStart() == 1 && insideMatch.getSpanEnd() == subexpression.size()) {
            return new Match(startIndex, startIndex + subexpression.size());
          }
        }
        startIndex++;
      }
      // No matches.
      return null;
    }
  }

  public static class ListPattern implements SExpressionPattern {
    private final SExpressionPattern first;
    private final SExpressionPattern second;
    
    public ListPattern(SExpressionPattern first, SExpressionPattern second) {
      this.first = Preconditions.checkNotNull(first);
      this.second = Preconditions.checkNotNull(second);
    }
    
    public Match getNextMatch(SExpression expression, int startIndex) {
      boolean matched = false;
      int matchStart = startIndex;
      int matchEnd = -1;
      while (!matched) {
        Match firstMatch = first.getNextMatch(expression, matchStart);
        if (firstMatch == null) {
          return null;
        }

        matchStart = firstMatch.getSpanStart();
        int nextStart = firstMatch.getSpanEnd();
        Match secondMatch = second.getNextMatch(expression, nextStart);
        if (secondMatch != null && secondMatch.getSpanStart() == nextStart) {
          matched = true;
          matchEnd = secondMatch.getSpanEnd();
        } else {
          matchStart++;
        }
      }
      return new Match(matchStart, matchEnd);
    }
  }

  private static class RepeatedPattern {
    private final SExpressionPattern pattern;
    
    public Iterator<Match> getMatches(SExpression expression, int startIndex) {
      return new RepeatedPatternMatchIterator();
      
      if (startIndex == endIndex) {
        return new int[] {startIndex, endIndex};
      } else {
        pattern.getNextMatch(startIndex, endIndex);
      }
    }
  }
  
  private static class RepeatedPatternMatchIterator implements Iterator<Match> {
    
    private Match next;
    private SExpression expression;
    private SExpressionPattern pattern;
    private int startIndex;
    
    public RepeatedPatternMatchIterator(SExpression expression,
        SExpressionPattern pattern, int startIndex) {
      this.expression = expression;
      this.pattern = pattern;
      this.startIndex = startIndex;
      this.next = new Match(startIndex, startIndex);
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public Match next() {
      if (next == null) {
        throw new NoSuchElementException();
      } else {
        Match current = next;
        next = pattern.
        
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
