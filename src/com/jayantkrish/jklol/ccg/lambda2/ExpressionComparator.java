package com.jayantkrish.jklol.ccg.lambda2;

/**
 * Interface for comparing expressions for equality.
 * 
 * @author jayant
 *
 */
public interface ExpressionComparator {

  /**
   * Returns {@code true} if {@code a} and {@code b} are
   * equal according to whatever comparison metric is 
   * used by this comparator.
   *  
   * @param a
   * @param b
   * @return
   */
  public boolean equals(Expression2 a, Expression2 b);
}
