package com.jayantkrish.jklol.util;

public interface Copyable<T> {

  /**
   * Copies the contents of this into {@code item}.
   * Mutating this object in the future should not
   * affect {@code item}, or vice versa.
   *  
   * @param item
   * @return
   */
  public void copyTo(T item);
  
  public T copy();
}
