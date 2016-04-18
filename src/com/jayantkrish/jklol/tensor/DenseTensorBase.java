package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.HeapUtils;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

/**
 * Common implementation of a dense tensor representation. The tensor is
 * represented as a single array, indexed by a combination of the dimensions of
 * the tensor key.
 * 
 * <p> Dense tensors only support tensors with up to {@code Integer.MAX_VALUE}
 * entries, which is the maximum array size addressable in Java. As a result,
 * {@code keyNum}s for dense tensors are representable using integers.
 * 
 * @author jayantk
 */
public class DenseTensorBase extends AbstractTensorBase {

  private static final long serialVersionUID = -4643174623400921192L;
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
    Preconditions.checkArgument(size <= Integer.MAX_VALUE, "Tensor size too big: %s", 
        Ints.asList(sizes));
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
    Preconditions.checkArgument(values.length == size, "Expected size: %s, was: %s", size, 
				values.length);

    this.values = values;
  }

  @Override
  public final int size() {
    return values.length;
  }

  @Override
  public final double getByIndex(int index) {
    return values[index];
  }

  @Override
  public final double getLogByIndex(int index) {
    return Math.log(values[index]);
  }

  @Override
  public final long indexToKeyNum(int index) {
    return (long) index;
  }

  @Override
  public final int keyNumToIndex(long keyNum) {
    return (int) keyNum;
  }

  public final int dimKeyToIndex(int[] dimKey) {
    return (int) dimKeyToKeyNum(dimKey);
  }

  @Override
  public Iterator<KeyValue> keyValueIterator() {
    return new KeyToKeyValueIterator(new IntegerArrayIterator(getDimensionSizes(), new int[0]),
        this);
  }

  @Override
  public Iterator<KeyValue> keyValuePrefixIterator(int[] keyPrefix) {
    return new KeyToKeyValueIterator(IntegerArrayIterator.createFromKeyPrefix(
        getDimensionSizes(), keyPrefix), this);
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

  public double[][] toMatrix(int[] rowDims, int[] colDims) {
    int[] dims = getDimensionNumbers();
    int[] sizes = getDimensionSizes();
    
    int numRows = 1;
    int[] rowMuls = new int[rowDims.length];
    for (int i = 0; i < rowDims.length; i++) {
      int index = Ints.indexOf(dims, rowDims[i]);
      rowMuls[i] = numRows;
      numRows *= sizes[index];
    }
    
    int numCols = 1;
    int[] colMuls = new int[colDims.length];
    for (int i = 0; i < colDims.length; i++) {
      int index = Ints.indexOf(dims, colDims[i]);
      colMuls[i] = numCols;
      numCols *= sizes[index];
    }

    double[][] matrix = new double[numRows][numCols];
    int[] dimKey = new int[sizes.length];
    for (int i = 0; i < values.length; i++) {
      keyNumToDimKey(i, dimKey);
      
      int rowInd = 0;
      for (int j = 0; j < rowDims.length; j++) {
        int index = Ints.indexOf(dims, rowDims[j]);
        rowInd += dimKey[index] * rowMuls[j];
      }

      int colInd = 0;
      for (int j = 0; j < colDims.length; j++) {
        int index = Ints.indexOf(dims, colDims[j]);
        colInd += dimKey[index] * colMuls[j];
      }

      matrix[rowInd][colInd] = values[i];
    }

    return matrix;
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
    Preconditions.checkArgument(otherInd == otherDimensionNums.length, "Cannot map tensor dimensions. My dimensions: %s. Other dimensions %s", 
        Arrays.toString(getDimensionNumbers()), Arrays.toString(otherDimensionNums));
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
}
