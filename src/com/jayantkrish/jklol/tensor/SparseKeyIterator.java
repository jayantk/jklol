package com.jayantkrish.jklol.tensor;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Preconditions;

public class SparseKeyIterator implements Iterator<int[]> {

  private int curIndex;
  private int finalIndex;
  private final long[] keyInts;
  private final TensorBase tensor;

  /**
   * 
   * The iterator iterates over all keys from {@code initialIndex} (inclusive)
   * to {@code finalIndex} (not inclusive).
   * 
   * @param keyInts
   * @param initialIndex
   * @param tensor
   */
  public SparseKeyIterator(long[] keyInts, int initialIndex, 
      int finalIndex, TensorBase tensor) {
    Preconditions.checkArgument(finalIndex <= keyInts.length);
    this.keyInts = Preconditions.checkNotNull(keyInts);
    this.curIndex = initialIndex;
    this.finalIndex = finalIndex;
    this.tensor = Preconditions.checkNotNull(tensor);
  }

  @Override
  public boolean hasNext() {
    return curIndex < finalIndex;
  }

  @Override
  public int[] next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    curIndex++;
    return tensor.keyNumToDimKey(keyInts[curIndex - 1]);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
