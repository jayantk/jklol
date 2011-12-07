package com.jayantkrish.jklol.tensor;

import java.util.Iterator;

/**
 * Common methods shared by {@link Tensor}s and {@link TensorBuilder}s. These
 * methods define a common access mechanism across both mutable and immutable
 * variants of tensors.
 * 
 * @author jayant
 */
public interface TensorBase {

  int[] getDimensionNumbers();

  int[] getDimensionSizes();

  /**
   * Gets an estimate of the size of this tensor, which corresponds to the
   * amount of work required to perform operations with it. For sparse tensors,
   * this is the number of nonzero values; for dense tensors, this is the total
   * number of values.
   * 
   * @return
   */
  int size();

  /**
   * Gets the value associated with {@code key} in {@code this}. The {@code i}th
   * element of {@code key} is the index into the {@code i}th dimension of
   * {@code this}'s multidimensional value array.
   * 
   * Requires {@code key.length == this.getDimensionNumbers().length}.
   * 
   * @param key
   * @return
   */
  double get(int... key);

  /**
   * Gets an iterator over all keys of {@code this} whose associated value is
   * nonzero. The returned iterator is guaranteed to iterate over keys with
   * nonzero value, and may optionally iterate over keys whose value is zero.
   * 
   * @return
   */
  Iterator<int[]> keyIterator();

  /**
   * Gets the Frobenius norm of this tensor, which is the square root of the sum
   * of each value squares of all values.
   * 
   * @return
   */
  double getL2Norm();
}
