package com.jayantkrish.jklol.util;

public class ArrayUtils {

  /**
   * Identical to {@code Arrays.copyOf}, but GWT compatible.
   */
  public static int[] copyOf(int[] old, int length) {
    int[] newArray = new int[length];
    int minLength = Math.min(old.length, length);
    System.arraycopy(old, 0, newArray, 0, minLength);
    return newArray;
  }

  /**
   * Identical to {@code Arrays.copyOf}, but GWT compatible.
   */
  public static double[] copyOf(double[] old, int length) {
    double[] newArray = new double[length];
    int minLength = Math.min(old.length, length);
    System.arraycopy(old, 0, newArray, 0, minLength);
    return newArray;
  }

  /**
   * Identical to {@code Arrays.copyOf}, but GWT compatible.
   */
  public static long[] copyOf(long[] old, int length) {
    long[] newArray = new long[length];
    int minLength = Math.min(old.length, length);
    System.arraycopy(old, 0, newArray, 0, minLength);
    return newArray;
  }

  /**
   * Identical to {@code Arrays.copyOf}, but GWT compatible.
   */
  public static String[] copyOf(String[] old, int length) {
    String[] newArray = new String[length];
    int minLength = Math.min(old.length, length);
    System.arraycopy(old, 0, newArray, 0, minLength);
    return newArray;
  }

  /**
   * Identical to {@code Arrays.copyOfRange}, but GWT compatible.
   */
  public static int[] copyOfRange(int[] old, int from, int to) {
    int length = to - from;
    int[] newArray = new int[length];
    int minLength = Math.min(old.length - from, length);
    System.arraycopy(old, from, newArray, 0, minLength);
    return newArray;
  }

  /**
   * Identical to {@code Arrays.copyOfRange}, but GWT compatible.
   */
  public static long[] copyOfRange(long[] old, int from, int to) {
    int length = to - from;
    long[] newArray = new long[length];
    int minLength = Math.min(old.length - from, length);
    System.arraycopy(old, from, newArray, 0, minLength);
    return newArray;
  }

  /**
   * Identical to {@code Arrays.copyOfRange}, but GWT compatible.
   */
  public static double[] copyOfRange(double[] old, int from, int to) {
    int length = to - from;
    double[] newArray = new double[length];
    int minLength = Math.min(old.length - from, length);
    System.arraycopy(old, from, newArray, 0, minLength);
    return newArray;
  }

  /**
   * Identical to {@code Arrays.copyOfRange}, but GWT compatible.
   */
  public static String[] copyOfRange(String[] old, int from, int to) {
    int length = to - from;
    String[] newArray = new String[length];
    int minLength = Math.min(old.length - from, length);
    System.arraycopy(old, from, newArray, 0, minLength);
    return newArray;
  }

  public static void copy(int[] src, int srcStartIndex, int[] dst, int dstStartIndex, int numToCopy) {
    for (int i = 0; i < numToCopy; i++) {
      dst[dstStartIndex + i] = src[srcStartIndex + i];
    }
  }

  public static boolean subarrayEquals(int[] array, int[] subarray, int startIndex) {
    for (int i = 0; i < subarray.length; i++) {
      if (array[i + startIndex] != subarray[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns an array of the form {@code [min, min + 1, ..., max - 1]}
   * 
   * @param min
   * @param max
   */
  public static int[] range(int min, int max) {
    int[] range = new int[max - min];
    for (int i = 0; i < (max - min); i++) {
      range[i] = i + min;
    }
    return range;
  }

  /**
   * Parses the string representations of integers in
   * {@code intStrings} and returns the resulting integer array.
   * 
   * @param intStrings
   * @return
   */
  public static int[] parseInts(String[] intStrings) {
    int[] values = new int[intStrings.length];
    for (int i = 0; i < intStrings.length; i++) {
      values[i] = Integer.parseInt(intStrings[i].trim());
    }
    return values;
  }

  /**
   * Parses the string representations of doubles in
   * {@code doubleStrings} and returns the resulting doubles array.
   * 
   * @param doubleStrings
   * @return
   */
  public static double[] parseDoubles(String[] doubleStrings) {
    double[] values = new double[doubleStrings.length];
    for (int i = 0; i < doubleStrings.length; i++) {
      values[i] = Double.parseDouble(doubleStrings[i].trim());
    }
    return values;
  }

  /**
   * Sorts a portion of the given key/value pairs by key. This method
   * sorts the section of {@code keys} from {@code startInd}
   * (inclusive) to {@code endInd} (not inclusive), simultaneously
   * swapping the corresponding entries of {@code values}.
   * 
   * @param keys
   * @param values
   * @param startInd
   * @param endInd
   */
  public static final void sortKeyValuePairs(long[] keys, double[] values,
      int startInd, int endInd) {
    // Base case.
    if (endInd - startInd <= 1) {
      return;
    }

    // Choose pivot.
    int pivotInd = (int) (Math.random() * (endInd - startInd)) + startInd;

    // Perform swaps to partition array around the pivot.
    swap(keys, values, startInd, pivotInd);
    pivotInd = startInd;

    for (int i = startInd + 1; i < endInd; i++) {
      if (keys[i] < keys[pivotInd]) {
        swap(keys, values, pivotInd, pivotInd + 1);
        if (i != pivotInd + 1) {
          swap(keys, values, pivotInd, i);
        }
        pivotInd++;
      }
    }

    // Recursively sort the subcomponents of the arrays.
    sortKeyValuePairs(keys, values, startInd, pivotInd);
    sortKeyValuePairs(keys, values, pivotInd + 1, endInd);
  }

  /**
   * Swaps the keys and values at {@code i} with those at {@code j}
   * 
   * @param keys
   * @param values
   * @param i
   * @param j
   */
  private static final void swap(long[] keys, double[] values, int i, int j) {
    long keySwap = keys[i];
    keys[i] = keys[j];
    keys[j] = keySwap;

    double swapValue = values[i];
    values[i] = values[j];
    values[j] = swapValue;
  }

  /**
   * Sorts a portion of the given key/value pairs by key. This method
   * sorts the section of {@code keys} from {@code startInd}
   * (inclusive) to {@code endInd} (not inclusive), simultaneously
   * swapping the corresponding entries of {@code values}.
   * <p>
   * {@code values} are treated like secondary keys during the sort.
   * If multiple entries in {@code keys} have the same value, these
   * keys are sorted by their values.
   * 
   * @param keys
   * @param values
   * @param startInd
   * @param endInd
   */
  public static final void sortKeyValuePairs(int[] keys, int[] values,
      int startInd, int endInd) {
    // Base case.
    if (endInd - startInd <= 1) {
      return;
    }

    // Choose pivot.
    int pivotInd = (int) (Math.random() * (endInd - startInd)) + startInd;

    // Perform swaps to partition array around the pivot.
    swap(keys, values, startInd, pivotInd);
    pivotInd = startInd;

    for (int i = startInd + 1; i < endInd; i++) {
      if (keys[i] < keys[pivotInd] || (keys[i] == keys[pivotInd] && values[i] < values[pivotInd])) {
        swap(keys, values, pivotInd, pivotInd + 1);
        if (i != pivotInd + 1) {
          swap(keys, values, pivotInd, i);
        }
        pivotInd++;
      }
    }

    // Recursively sort the subcomponents of the arrays.
    sortKeyValuePairs(keys, values, startInd, pivotInd);
    sortKeyValuePairs(keys, values, pivotInd + 1, endInd);
  }

  /**
   * Swaps the keys and values at {@code i} with those at {@code j}
   * 
   * @param keys
   * @param values
   * @param i
   * @param j
   */
  private static final void swap(int[] keys, int[] values, int i, int j) {
    int keySwap = keys[i];
    keys[i] = keys[j];
    keys[j] = keySwap;

    int swapValue = values[i];
    values[i] = values[j];
    values[j] = swapValue;
  }
  
  /**
   * Sorts a portion of the given key/value pairs by key. This method
   * sorts the section of {@code keys} from {@code startInd}
   * (inclusive) to {@code endInd} (not inclusive), simultaneously
   * swapping the corresponding entries of {@code values}.
   * <p>
   * {@code values} are treated like secondary keys during the sort.
   * If multiple entries in {@code keys} have the same value, these
   * keys are sorted by their values.
   * 
   * @param keys
   * @param values
   * @param startInd
   * @param endInd
   */
  public static final <T> void sortKeyValuePairs(int[] keys, T[] values,
      int startInd, int endInd) {
    // Base case.
    if (endInd - startInd <= 1) {
      return;
    }

    // Choose pivot.
    int pivotInd = (int) (Math.random() * (endInd - startInd)) + startInd;

    // Perform swaps to partition array around the pivot.
    swap(keys, values, startInd, pivotInd);
    pivotInd = startInd;

    for (int i = startInd + 1; i < endInd; i++) {
      if (keys[i] < keys[pivotInd]) {
        swap(keys, values, pivotInd, pivotInd + 1);
        if (i != pivotInd + 1) {
          swap(keys, values, pivotInd, i);
        }
        pivotInd++;
      }
    }

    // Recursively sort the subcomponents of the arrays.
    sortKeyValuePairs(keys, values, startInd, pivotInd);
    sortKeyValuePairs(keys, values, pivotInd + 1, endInd);
  }

  /**
   * Swaps the keys and values at {@code i} with those at {@code j}
   * 
   * @param keys
   * @param values
   * @param i
   * @param j
   */
  private static final <T> void swap(int[] keys, T[] values, int i, int j) {
    int keySwap = keys[i];
    keys[i] = keys[j];
    keys[j] = keySwap;

    T swapValue = values[i];
    values[i] = values[j];
    values[j] = swapValue;
  }
  
  /**
   * Sorts a portion of the given key/value pairs by key. This method
   * sorts the section of {@code keys} from {@code startInd}
   * (inclusive) to {@code endInd} (not inclusive), simultaneously
   * swapping the corresponding entries of {@code values}. Every element in 
   * each array in {@code values} is swapped.
   * 
   * @param keys
   * @param values
   * @param startInd
   * @param endInd
   */
  public static final void sortKeyValuePairs(int[] keys, Object[][] values,
      int startInd, int endInd) {
    // Base case.
    if (endInd - startInd <= 1) {
      return;
    }

    // Choose pivot.
    int pivotInd = (int) (Math.random() * (endInd - startInd)) + startInd;

    // Perform swaps to partition array around the pivot.
    swap(keys, values, startInd, pivotInd);
    pivotInd = startInd;

    for (int i = startInd + 1; i < endInd; i++) {
      if (keys[i] < keys[pivotInd]) {
        swap(keys, values, pivotInd, pivotInd + 1);
        if (i != pivotInd + 1) {
          swap(keys, values, pivotInd, i);
        }
        pivotInd++;
      }
    }

    // Recursively sort the subcomponents of the arrays.
    sortKeyValuePairs(keys, values, startInd, pivotInd);
    sortKeyValuePairs(keys, values, pivotInd + 1, endInd);
  }

  /**
   * Swaps the keys and values at {@code i} with those at {@code j}
   * 
   * @param keys
   * @param values
   * @param i
   * @param j
   */
  private static final void swap(int[] keys, Object[][] values, int i, int j) {
    int keySwap = keys[i];
    keys[i] = keys[j];
    keys[j] = keySwap;

    Object swapValue;
    for (int k = 0; k < values.length; k++) {
      swapValue = values[k][i];
      values[k][i] = values[k][j];
      values[k][j] = swapValue;
    }
  }

  /**
   * Sorts a portion of the given key/value pairs by key. This method
   * sorts the section of {@code keys} from {@code startInd}
   * (inclusive) to {@code endInd} (not inclusive), simultaneously
   * swapping the corresponding entries of {@code values}.
   * <p>
   * {@code probs} are treated like secondary keys during the sort.
   * If multiple entries in {@code keys} have the same value, these
   * keys are sorted by their probs.
   * 
   * @param keys
   * @param values
   * @param probs
   * @param startInd
   * @param endInd
   */
  public static final <T> void sortKeyValuePairs(long[] keys, T[] values,
      double[] probs, int startInd, int endInd) {
    // Base case.
    if (endInd - startInd <= 1) {
      return;
    }

    // Choose pivot.
    int pivotInd = (int) (Math.random() * (endInd - startInd)) + startInd;

    // Perform swaps to partition array around the pivot.
    swap(keys, values, probs, startInd, pivotInd);
    pivotInd = startInd;

    for (int i = startInd + 1; i < endInd; i++) {
      if (keys[i] < keys[pivotInd]
          || (keys[i] == keys[pivotInd] && probs[i] < probs[pivotInd])
          || (keys[i] == keys[pivotInd] && probs[i] == probs[pivotInd] && Math.random() < 0.5)) {
        swap(keys, values, probs, pivotInd, pivotInd + 1);
        if (i != pivotInd + 1) {
          swap(keys, values, probs, pivotInd, i);
        }
        pivotInd++;
      }
    }

    // Recursively sort the subcomponents of the arrays.
    sortKeyValuePairs(keys, values, probs, startInd, pivotInd);
    sortKeyValuePairs(keys, values, probs, pivotInd + 1, endInd);
  }

  /**
   * Swaps the keys and values at {@code i} with those at {@code j}
   * 
   * @param keys
   * @param values
   * @param i
   * @param j
   */
  private static final <T> void swap(long[] keys, T[] values, double[] probs, int i, int j) {
    long keySwap = keys[i];
    keys[i] = keys[j];
    keys[j] = keySwap;

    T swapValue = values[i];
    values[i] = values[j];
    values[j] = swapValue;
    
    double swapProb = probs[i];
    probs[i] = probs[j];
    probs[j] = swapProb;
  }

  private ArrayUtils() {
    // Prevent instantiation.
  }
}