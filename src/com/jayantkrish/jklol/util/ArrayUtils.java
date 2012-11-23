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

  public static boolean subarrayEquals(int[] array, int[] subarray, int startIndex) {
      for (int i = 0; i < subarray.length; i++) {
	  if (array[i + startIndex] != subarray[i]) {
	      return false;
	  }
      }
      return true;
  }
}