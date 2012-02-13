package com.jayantkrish.jklol.tensor;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;

/**
 * An {@code Iterator} for efficiently accessing keys and values of
 * {@link SparseTensor}s.
 * 
 * @author jayantk
 */
public class SparseKeyValueIterator implements Iterator<KeyValue> {

  private int curIndex;
  private int finalIndex;
  private final long[] keyInts;
  private final double[] values;
  private final TensorBase tensor;

  private final KeyValue keyValue;
  
  /**
   * 
   * The iterator iterates over all keys from {@code initialIndex} (inclusive)
   * to {@code finalIndex} (not inclusive).
   * 
   * @param keyInts
   * @param initialIndex
   * @param tensor
   */
  public SparseKeyValueIterator(long[] keyInts, double[] values, int initialIndex, 
      int finalIndex, TensorBase tensor) {
    Preconditions.checkArgument(finalIndex <= keyInts.length);
    Preconditions.checkArgument(keyInts.length == values.length);
    this.keyInts = Preconditions.checkNotNull(keyInts);
    this.values = values;
    this.curIndex = initialIndex;
    this.finalIndex = finalIndex;
    this.tensor = Preconditions.checkNotNull(tensor);
    
    this.keyValue = new KeyValue(new int[tensor.getDimensionNumbers().length], 0.0);
  }

  @Override
  public boolean hasNext() {
    return curIndex < finalIndex;
  }

  @Override
  public KeyValue next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    // This call mutates the key field of {@code keyValue}.
    tensor.keyNumToDimKey(keyInts[curIndex], keyValue.getKey());
    keyValue.setValue(values[curIndex]);
    curIndex++;
    return keyValue;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
