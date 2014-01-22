package com.jayantkrish.jklol.cvsm.eval;

import com.google.common.base.Preconditions;

/**
 * A LISP cons cell, which is a tuple. Nested cons cells
 * are used to implement lists (essentially a linked list).
 * 
 * @author jayantk
 */
public class ConsValue {
  private final Object car;
  private final Object cdr;

  public ConsValue(Object car, Object cdr) {
    this.car = Preconditions.checkNotNull(car);
    this.cdr = Preconditions.checkNotNull(cdr);
  }

  /**
   * Gets the first element of this tuple.
   * 
   * @return
   */
  public Object getCar() {
    return car;
  }

  /**
   * Gets the second element of this tuple. Returns
   * {@code ConstantValue.NIL} if this is the last
   * element in a list.
   * 
   * @return
   */
  public Object getCdr() {
    return cdr;
  }
}
