package com.jayantkrish.jklol.tensor;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.HeapUtils;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

/**
 * Dense tensors only support tensors with up to {@code Integer.MAX_VALUE}
 * entries, which is the maximum array size addressable in Java. As a result,
 * {@code keyNum}s for dense tensors are representable using integers.
 * 
 * @author jayantk
 */
public class DenseTensorBase extends AbstractTensorBase {

  // Stores the values of each key in this. Accessible to subclasses for
  // fast mathematical operations.
  protected final double[] values;

  /**
   * Creates a tensor with an uninitialized values array.
   * 
   * @param dimensions
   * @param sizes
   */
  public DenseTensorBase(int[] dimensions, int[] sizes) {
    super(dimensions, sizes);

    long size = 1;
    for (int i = 0; i < sizes.length; i++) {
      size *= sizes[i];
    }
    Preconditions.checkArgument(size <= Integer.MAX_VALUE);
    values = new double[(int) size];
  }

  /**
   * Creates a tensor with the given values array.
   * 
   * @param dimensions
   * @param sizes
   */
  protected DenseTensorBase(int[] dimensions, int[] sizes, double[] values) {
    super(dimensions, sizes);

    // Check the size of the values array.
    int size = 1;
    for (int i = 0; i < sizes.length; i++) {
      size *= sizes[i];
    }
    Preconditions.checkArgument(values.length == size);

    this.values = values;
  }

  @Override
  public int size() {
    return values.length;
  }

  @Override
  public double getByIndex(int index) {
    return values[index];
  }

  @Override
  public double getLogByIndex(int index) {
    return Math.log(values[index]);
  }

  @Override
  public long indexToKeyNum(int index) {
    return (long) index;
  }

  @Override
  public int keyNumToIndex(long keyNum) {
    return (int) keyNum;
  }

  public int dimKeyToIndex(int[] dimKey) {
    return (int) dimKeyToKeyNum(dimKey);
  }

  @Override
  public Iterator<KeyValue> keyValueIterator() {
    return new KeyToKeyValueIterator(new IntegerArrayIterator(getDimensionSizes(), new int[0]),
        this);
  }

  @Override
  public Iterator<KeyValue> keyValuePrefixIterator(int[] keyPrefix) {
    int[] dimSizes = getDimensionSizes();
    int[] sizesForIteration = new int[dimSizes.length - keyPrefix.length];
    for (int i = keyPrefix.length; i < dimSizes.length; i++) {
      sizesForIteration[i - keyPrefix.length] = dimSizes[i];
    }
    return new KeyToKeyValueIterator(new IntegerArrayIterator(sizesForIteration, keyPrefix),
        this);
  }

  @Override
  public double getL2Norm() {
    double sumSquares = 0.0;
    for (int i = 0; i < values.length; i++) {
      sumSquares += values[i] * values[i];
    }
    return Math.sqrt(sumSquares);
  }

  @Override
  public double getTrace() {
    double sum = 0.0;
    for (int i = 0; i < values.length; i++) {
      sum += values[i];
    }
    return sum;
  }

  @Override
  public long[] getLargestValues(int n) {
    return HeapUtils.findLargestItemIndexes(values, n);
  }

  protected int[] getDimensionMapping(int[] otherDimensionNums) {
    int[] mapping = new int[otherDimensionNums.length];
    int otherInd = 0;
    for (int i = 0; i < getDimensionNumbers().length; i++) {
      if (otherInd < otherDimensionNums.length && getDimensionNumbers()[i] == otherDimensionNums[otherInd]) {
        mapping[otherInd] = i;
        otherInd++;
      }
    }
    // Ensure that the mapping is fully initialized.
    Preconditions.checkArgument(otherInd == otherDimensionNums.length);
    return mapping;
  }

  protected class SliceIndexIterator implements Iterator<int[]> {

    private final Iterator<int[]> variableDimensionIterator;
    private final int[] variableDimensionInds;
    private final int[] currentIndex;

    public SliceIndexIterator(int[] dimensionSizes, int[] fixedIndices) {

      currentIndex = new int[dimensionSizes.length];
      List<Integer> variableDimensionIndList = Lists.newArrayList();
      List<Integer> variableDimensionSizes = Lists.newArrayList();
      for (int i = 0; i < fixedIndices.length; i++) {
        if (fixedIndices[i] == -1) {
          variableDimensionSizes.add(dimensionSizes[i]);
          variableDimensionIndList.add(i);
        } else {
          currentIndex[i] = fixedIndices[i];
        }
      }

      variableDimensionIterator = new IntegerArrayIterator(Ints.toArray(variableDimensionSizes), new int[0]);
      variableDimensionInds = Ints.toArray(variableDimensionIndList);
    }

    @Override
    public boolean hasNext() {
      return variableDimensionIterator.hasNext();
    }

    @Override
    public int[] next() {
      int[] nextKey = variableDimensionIterator.next();
      for (int i = 0; i < nextKey.length; i++) {
        currentIndex[variableDimensionInds[i]] = nextKey[i];
      }
      return currentIndex;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  protected class KeyToKeyValueIterator implements Iterator<KeyValue> {

    private final Iterator<int[]> keyIterator;
    private final TensorBase tensor;

    private final KeyValue keyValue;

    public KeyToKeyValueIterator(Iterator<int[]> keyIterator, TensorBase tensor) {
      this.keyIterator = Preconditions.checkNotNull(keyIterator);
      this.tensor = Preconditions.checkNotNull(tensor);

      this.keyValue = new KeyValue(null, 0.0);
    }

    @Override
    public boolean hasNext() {
      return keyIterator.hasNext();
    }

    @Override
    public KeyValue next() {
      int[] key = keyIterator.next();
      keyValue.setKey(key);
      keyValue.setValue(tensor.getByDimKey(key));
      return keyValue;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
