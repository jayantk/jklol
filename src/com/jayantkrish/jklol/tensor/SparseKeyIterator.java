package com.jayantkrish.jklol.tensor;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class SparseKeyIterator implements Iterator<int[]> {

  private int curIndex;
  private final List<Integer> keyInts;
  private final TensorBase tensor;

  public SparseKeyIterator(List<Integer> keyInts, TensorBase tensor) {
    this.keyInts = keyInts;
    this.curIndex = 0;
    this.tensor = tensor;
  }

  @Override
  public boolean hasNext() {
    return curIndex < keyInts.size();
  }

  @Override
  public int[] next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    curIndex++;
    return tensor.keyIntToDimKey(keyInts.get(curIndex - 1));
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
