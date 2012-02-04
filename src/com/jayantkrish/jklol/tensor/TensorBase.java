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
  
  int numDimensions();

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
   * {@code this}'s multidimensional value array. Requires
   * {@code key.length == this.getDimensionNumbers().length}.
   * 
   * @param key
   * @return
   */
  double getByDimKey(int... key);

  /**
   * Gets the value associated with {@code keyNum}, which is interpreted as a
   * key by successively mod'ing it by the size of each dimension of
   * {@code this}. Equivalent {@code getByDimKey(convertToDimKey(keyNum))}.
   * 
   * @param keyNum
   * @return
   */
  double get(long keyNum);
  
  double getByIndex(int index);

  int[] keyNumToDimKey(long keyNum);

  long dimKeyToKeyNum(int[] dimKey);
  
  int keyNumToIndex(long keyNum);
    
  long indexToKeyNum(int index);

  /**
   * Gets an iterator over all keys of {@code this} whose associated value is
   * nonzero. The returned iterator is guaranteed to iterate over keys with
   * nonzero value, and may optionally iterate over keys whose value is zero.
   * The {@code int[]} returned by the iterator may be modified later by the
   * iterator, and must be copied in order to retain their value.
   * 
   * @return
   */
  Iterator<int[]> keyIterator();

  /**
   * Gets an iterator over the subset of keys in {@code this} that begin with
   * {@code keyPrefix}. That is, the first {@code keyPrefix.length} dimensions
   * of {@code key} are equal to their corresponding values in {@code keyPrefix}
   * . The returned iterator may not iterate over keys whose value is 0.
   * 
   * @param keyPrefix
   * @return
   */
  Iterator<int[]> keyPrefixIterator(int[] keyPrefix);
  
  /**
   * Gets an iterator over all key/value pairs of {@code this}. The iterator is
   * guaranteed to iterate over all keys with nonzero values, and may optionally
   * iterate over keys with value 0. This method is more efficient than
   * {@link #keyIterator()} for accessing all values in {@code this}.
   * 
   * @return
   */
  // Iterator<KeyValue> entryIterator();

  /**
   * Gets the Frobenius norm of this tensor, which is the square root of the sum
   * of each value squares of all values.
   * 
   * @return
   */
  double getL2Norm();

  public class KeyValue {
    private int[] key;
    private double value;

    public KeyValue(int[] key, double value) {
      this.key = key;
      this.value = value;
    }

    public int[] getKey() {
      return key;
    }

    public double getValue() {
      return value;
    }
  }
}
