package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Iterator;

/**
 * An iterator over arrays of integers, from [0, ..., 0, 0] to [max_1,
 * max_2, ...], where the maximum value for each dimension is
 * specified during construction. The iteration treats the last
 * element of the array as the least significant bit, essentially
 * iterating from 0 to max_1 * max_2 * ...
 * 
 * The iterator also allows for an optional prefix, which is prepended
 * to all returned arrays.
 * 
 * @author jayantk
 */
public class IntegerArrayIterator implements Iterator<int[]> {

  private int[] currentValues;
  private int[] finalValues;

  private final int prefixLength;
  private final int[] nextVal;

  public IntegerArrayIterator(int[] dimensionSizes, int[] prefix) {
    finalValues = new int[dimensionSizes.length + 1];
    // The first index is 1 and used as a test for the end of
    // iteration.
    finalValues[0] = 1;
    for (int i = 0; i < dimensionSizes.length; i++) {
      finalValues[i + 1] = dimensionSizes[i] - 1;
    }

    currentValues = new int[finalValues.length];
    Arrays.fill(currentValues, 0);

    prefixLength = prefix.length;
    nextVal = new int[dimensionSizes.length + prefix.length];
    System.arraycopy(prefix, 0, nextVal, 0, prefix.length);
  }

  /**
   * Creates an iterator over all assignments to the final
   * {@code dimensionSizes.length - keyPrefix.length} dimensions in
   * {@code dimensionSizes}.
   * 
   * @param dimensionSizes
   * @param keyPrefix
   * @return
   */
  public static IntegerArrayIterator createFromKeyPrefix(int[] dimensionSizes, int[] keyPrefix) {    
    int[] sizesForIteration = new int[dimensionSizes.length - keyPrefix.length];
    for (int i = keyPrefix.length; i < dimensionSizes.length; i++) {
      sizesForIteration[i - keyPrefix.length] = dimensionSizes[i];
    }
    return new IntegerArrayIterator(sizesForIteration, keyPrefix);
  }

  @Override
  public boolean hasNext() {
    return !(currentValues[0] == finalValues[0]);
  }

  @Override
  public int[] next() {
    System.arraycopy(currentValues, 1, nextVal, prefixLength,
        currentValues.length - 1);

    incrementCurrentValue();
    return nextVal;
  }

  /*
   * Advances the internal state of the iterator (currentValues) to
   * the next value.
   */
  private void incrementCurrentValue() {
    currentValues[currentValues.length - 1]++;
    int i = currentValues.length - 1;
    while (i > 0 &&
        currentValues[i] > finalValues[i]) {
      currentValues[i] = 0;
      currentValues[i - 1]++;
      i--;
    }
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
