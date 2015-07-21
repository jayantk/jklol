package com.jayantkrish.jklol.ccg.lambda2;

import com.google.common.base.Preconditions;

public class SExpressionPatternMatcher {

  public static interface Expression2Pattern {
    
    public Match getNextMatch(Expression2 expression, int startIndex);
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

  public static class ConstantExpression2Pattern implements Expression2Pattern {
    private String constantName;

    public ConstantExpression2Pattern(String constantName) {
      this.constantName = Preconditions.checkNotNull(constantName);
    }

    public Match getNextMatch(Expression2 expression, int startIndex) {
      while (startIndex < expression.size() && !matches(expression.getSubexpression(startIndex)))  {
        startIndex++;
      }
      
      if (startIndex >= expression.size()) {
        return null;
      } else {
        return new Match(startIndex, startIndex + 1);
      }
    }

    public boolean matches(Expression2 subexpression) {
      return subexpression.isConstant() && subexpression.getConstant().equals(constantName);
    }
  }

  public static class SubexpressionPattern implements Expression2Pattern {
    private final Expression2Pattern insidePattern;
    
    // Matches any subexpression.
    public SubexpressionPattern() {
      insidePattern = null;
    }
    
    public SubexpressionPattern(Expression2Pattern insidePattern) {
      this.insidePattern = insidePattern;
    }

    public Match getNextMatch(Expression2 expression, int startIndex) {
      while (startIndex < expression.size()) {
        Expression2 subexpression = expression.getSubexpression(startIndex);
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

  public static class ListPattern implements Expression2Pattern {
    private final Expression2Pattern first;
    private final Expression2Pattern second;
    
    public ListPattern(Expression2Pattern first, Expression2Pattern second) {
      this.first = Preconditions.checkNotNull(first);
      this.second = Preconditions.checkNotNull(second);
    }
    
    public Match getNextMatch(Expression2 expression, int startIndex) {
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

  /*
  private static class RepeatedPattern {
    private final Expression2Pattern pattern;
    
    public Iterator<Match> getMatches(Expression2 expression, int startIndex) {
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
    private Expression2 expression;
    private Expression2Pattern pattern;
    private int startIndex;
    
    public RepeatedPatternMatchIterator(Expression2 expression,
        Expression2Pattern pattern, int startIndex) {
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
  
    // warning: need to freshen rest / body to avoid capturing parts of val
  // ((lambda ?x ?rest+ ?body) ?val ?valrest+) -> 
  // ((lambda ?rest sub(?body, ?x, ?val)) ?valrest)
  //
  // ((lambda ?x ?body) ?val) ->
  // sub(?body, ?x, ?val)
  //
  // List valued variables (* and +) are inlined automatically
  // (and<t,t> ?first* (and<t,t> ?inner*) ?last*)
  // (and<t,t> ?first ?inner ?last)
  //
  // (and ?first* (exists ?vars* ?body) ?last*)
  // (exists ?vars (and ?first ?body ?last))
  //
  // (exists ?vars* (exists ?vars2* ?body))
  // (exists ?vars* ?vars2* body)

*/
}
