package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Iterator;

/**
 * An iterator over arrays of integers, from [0, ..., 0, 0] to [max_1, max_2,
 * ...], where the maximum value for each dimension is specified during
 * construction.
 * 
 * @author jayantk
 */
public class IntegerArrayIterator implements Iterator<int[]> {

  private int[] currentValues;
  private int[] finalValues;
  
  private final int[] nextVal;

  public IntegerArrayIterator(int[] dimensionSizes) {
    finalValues = Arrays.copyOf(dimensionSizes, dimensionSizes.length + 1);

    // The final index is 1 and used as a test for the end of iteration.
    this.finalValues[finalValues.length - 1] = 1;
    
    // The code in this iterator goes from 0 to finalValues, inclusive.
    for (int i = 0; i < dimensionSizes.length; i++) {
      finalValues[i]--;
    }

    currentValues = new int[finalValues.length];
    Arrays.fill(currentValues, 0);

    nextVal = new int[dimensionSizes.length];
  }

  @Override
  public boolean hasNext() {
    return !(currentValues[currentValues.length - 1] == finalValues[finalValues.length - 1]);
  }

  @Override
  public int[] next() {
    System.arraycopy(currentValues, 0, nextVal, 0, nextVal.length);
    
    incrementCurrentValue();
    return nextVal;
  }
  
  /*
	 * Advances the internal state of the iterator (currentValues) to the next value.
	 */
	private void incrementCurrentValue() {
		currentValues[0]++;
		int i = 0;
		while (i < currentValues.length - 1 && 
				currentValues[i] > finalValues[i]) {
			currentValues[i] = 0;
			currentValues[i + 1]++;
			i++;
		}
	}

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
