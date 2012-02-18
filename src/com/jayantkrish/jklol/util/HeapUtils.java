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

  public static final void swap(long[] heapKeys, double[] heapValues, int ind1, int ind2) {
    long tmpKey = heapKeys[ind1];
    double tmpValue = heapValues[ind1];
    heapKeys[ind1] = heapKeys[ind2];
    heapValues[ind1] = heapValues[ind2];
    heapKeys[ind2] = tmpKey;
    heapValues[ind2] = tmpValue;
  }
}
