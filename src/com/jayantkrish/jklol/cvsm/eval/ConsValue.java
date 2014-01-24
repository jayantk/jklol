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
  
  @Override
  public String toString() {
    return "(" + car.toString() + " " + cdr.toString() + ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((car == null) ? 0 : car.hashCode());
    result = prime * result + ((cdr == null) ? 0 : cdr.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ConsValue other = (ConsValue) obj;
    if (car == null) {
      if (other.car != null)
        return false;
    } else if (!car.equals(other.car))
      return false;
    if (cdr == null) {
      if (other.cdr != null)
        return false;
    } else if (!cdr.equals(other.cdr))
      return false;
    return true;
  }
}
