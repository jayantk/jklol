package com.jayantkrish.jklol.ccg.lambda2;

import com.google.common.base.Preconditions;

/**
 * Comparator that checks equality based on the literal textual
 * equality of simplified expressions.
 * 
 * @author jayant
 *
 */
public class SimplificationComparator implements ExpressionComparator {

  private final ExpressionSimplifier simplifier;
  
  public SimplificationComparator(ExpressionSimplifier simplifier) {
    this.simplifier = Preconditions.checkNotNull(simplifier);
  }

  @Override
  public boolean equals(Expression2 a, Expression2 b) {
    Expression2 simpleA = simplifier.apply(a);
    Expression2 simpleB = simplifier.apply(b);
    return simpleA.equals(simpleB);
  }
}
