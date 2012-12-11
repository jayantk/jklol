package com.jayantkrish.jklol.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Preconditions;

/**
 * An iterator over permutations of integers from 0...(n -1).
 * 
 * @author jayantk
 */
public class PermutationIterator implements Iterator<int[]> {

  private int[] permutation;
  private long numLeft;
  private long total;

  public PermutationIterator(int n) {
    // 20 is the largest permutation size that fits in a java long.
    Preconditions.checkArgument(n >= 0 && n < 20);
    permutation = new int[n];
    total = getFactorial(n);
    numLeft = total;

    for (int i = 0; i < permutation.length; i++) {
      permutation[i] = i;
    }
  }

  private static long getFactorial(int n) {
    long factorial = 1;
    for (int i = n; i > 1; i--) {
      factorial *= i;
    }
    return factorial;
  }

  @Override
  public boolean hasNext() {
    return numLeft > 0;
  }

  @Override
  public int[] next() {
    if (numLeft == 0) {
      throw new NoSuchElementException();
    }

    if (numLeft == total) {
      numLeft--;
      return ArrayUtils.copyOf(permutation, permutation.length);
    }

    // Find largest index j with a[j] < a[j+1]
    int j = permutation.length - 2;
    while (permutation[j] > permutation[j + 1]) {
      j--;
    }

    // Find index k such that a[k] is smallest integer
    // greater than a[j] to the right of a[j]
    int k = permutation.length - 1;
    while (permutation[j] > permutation[k]) {
      k--;
    }

    // Interchange a[j] and a[k]
    int temp = permutation[k];
    permutation[k] = permutation[j];
    permutation[j] = temp;

    // Put tail end of permutation after jth position in increasing order
    int r = permutation.length - 1;
    int s = j + 1;

    while (r > s) {
      temp = permutation[s];
      permutation[s] = permutation[r];
      permutation[r] = temp;
      r--;
      s++;
    }

    numLeft--;
    return ArrayUtils.copyOf(permutation, permutation.length);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("PermutationIterator.remove() is not supported.");
  }
}
