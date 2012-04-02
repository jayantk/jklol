package com.jayantkrish.jklol.util;

/**
 * Functions for manipulating arrays of primitives as if they were heaps.
 * 
 * @author jayantk
 */
public final class HeapUtils {

  /**
   * Adds {@code newKey} with value {@code newValue} to the heap represented by
   * {@code heapKeys} and {@code heapValues}. {@code heapSize} is the current
   * size of the heap; after calling this method, the heap's size will grow by
   * 1.
   * 
   * @param heapKeys
   * @param heapValues
   * @param heapSize
   * @param newKey
   * @param newValue
   */
  public static final void offer(long[] heapKeys, double[] heapValues, int heapSize,
      long newKey, double newValue) {
    heapKeys[heapSize] = newKey;
    heapValues[heapSize] = newValue;

    int curIndex = heapSize;
    int parentIndex = (curIndex - 1) / 2;
    while (heapValues[curIndex] < heapValues[parentIndex]) {
      swap(heapKeys, heapValues, curIndex, parentIndex);
      curIndex = parentIndex;
      // Note that if curIndex = 0, parentIndex = curIndex so the loop test
      // will become false.
      parentIndex = (curIndex - 1) / 2;
    }
  }

  /**
   * Removes the smallest key/value pair from the heap represented by
   * {@code heapKeys} and {@code heapValues}. After calling this method, the
   * size of the heap shrinks by 1.
   * 
   * @param heapKeys
   * @param heapValues
   * @param heapSize
   */
  public static final void removeMin(long[] heapKeys, double[] heapValues, int heapSize) {
    heapValues[0] = heapValues[heapSize - 1];
    heapKeys[0] = heapKeys[heapSize - 1];

    int curIndex = 0;
    int leftIndex, rightIndex, minIndex;
    boolean done = false;
    while (!done) {
      done = true;
      leftIndex = 1 + (curIndex * 2);
      rightIndex = leftIndex + 1;

      minIndex = -1;
      if (rightIndex < heapSize) {
        minIndex = heapValues[leftIndex] <= heapValues[rightIndex] ? leftIndex : rightIndex;
      } else if (leftIndex < heapSize) {
        minIndex = leftIndex;
      }

      if (minIndex != -1 && heapValues[minIndex] < heapValues[curIndex]) {
        swap(heapKeys, heapValues, curIndex, minIndex);
        done = false;
        curIndex = minIndex;
      }
    }
  }

  /**
   * Finds the {@code n} largest values in {@code values}, returning their
   * indexes in {@code values}. The returned indexes are sorted in descending
   * order by their value, i.e., the 0th element is the index of the maximum
   * value in {@code values}.
   * <p>
   * To avoid reimplementing heaps with ints, the indexes are returned as
   * {@code long}s. They can be cast back to {@code int}s to access the elements
   * of {@code values}.
   * 
   * @param keys
   * @param values
   * @param n
   * @return
   */
  public static final long[] findLargestItemIndexes(double[] values, int n) {
    long[] heapKeys = new long[n + 1];
    double[] heapValues = new double[n + 1];
    int heapSize = 0;

    for (int i = 0; i < values.length; i++) {
      offer(heapKeys, heapValues, heapSize, i, values[i]);
      heapSize++;

      if (heapSize > n) {
        removeMin(heapKeys, heapValues, heapSize);
        heapSize--;
      }
    }

    long[] returnKeys = new long[heapSize];
    while (heapSize > 0) {
      returnKeys[heapSize - 1] = heapKeys[0];
      removeMin(heapKeys, heapValues, heapSize);
      heapSize--;
    }
    return returnKeys;
  }

  public static final void swap(long[] heapKeys, double[] heapValues, int ind1, int ind2) {
    long tmpKey = heapKeys[ind1];
    double tmpValue = heapValues[ind1];
    heapKeys[ind1] = heapKeys[ind2];
    heapValues[ind1] = heapValues[ind2];
    heapKeys[ind2] = tmpKey;
    heapValues[ind2] = tmpValue;
  }
}
