package com.jayantkrish.jklol.util;

import java.util.Iterator;

import com.google.common.base.Preconditions;


/**
 * An iterator over subsets of a collection of {@code n}
 * elements.
 * 
 * @author jayant
 *
 */
public class SubsetIterator implements Iterator<boolean[]> {

  private final int n;
  private int current;
  private int end;

  public SubsetIterator(int n) {
    Preconditions.checkArgument(n >= 0);
    this.n = n;
    this.current = 0;
    this.end = 1 << n;
  }

  @Override
  public boolean hasNext() {
    return current < end;
  }

  @Override
  public boolean[] next() {
    boolean[] next = new boolean[n];
    for (int i = 0; i < n; i++) {
      next[i] = (((current >> i) & 1) == 1);
    }
    current++;
    return next;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
