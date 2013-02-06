package com.jayantkrish.jklol.ccg.lambda;

import java.io.Serializable;
import java.util.Set;

/**
 * A LISP-style S-expression.
 * 
 * @author jayantk
 */
public interface Expression extends Serializable {

  /**
   * Gets the set of unbound variables in this expression.
   * 
   * @return
   */
  Set<ConstantExpression> getFreeVariables();
  
  void getFreeVariables(Set<ConstantExpression> accumulator);

  /**
   * Replaces the free variable named {@code constant} by
   * {@code replacement}.
   * 
   * @param constant
   * @param replacement
   * @return
   */
  Expression substitute(ConstantExpression constant, Expression replacement);

  Expression simplify();

  @Override
  int hashCode();

  @Override
  boolean equals(Object o);
}
