package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
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
   * Gets an array whose vector product with a {@code dimKey} constructs its
   * corresponding {@code keyNum}. This method should only be used when
   * performance is an issue; in other cases, use {@link #dimKeyToKeyNum(int[])}
   * .
   * 
   * @return
   */
  long[] getDimensionOffsets();

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
   * A keyNum {@code k} is valid for {@code this} if and only if {@code 0 <= k < this.maxKeyNum()}.
   *  
   * @return
   */
  public long getMaxKeyNum();
  
  /**
   * Gets the value associated with {@code keyNum}, which is interpreted as a
   * key by successively mod'ing it by the size of each dimension of
   * {@code this}. Equivalent {@code getByDimKey(convertToDimKey(keyNum))}.
   * 
   * @param keyNum
   * @return
   */
  double get(long keyNum);

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
  
  double getByIndex(int index);

  /**
   * Gets the log of the value associated with {@code keyNum}.
   * 
   * @param keyNum
   * @return
   */
  double getLog(long keyNum);

  double getLogByDimKey(int... key);
  
  double getLogByIndex(int index);

  int[] keyNumToDimKey(long keyNum);

  /**
   * Same as {@link #keyNumToDimKey(long)}, except that {@code dimKey} is
   * overwritten with the dim key. This method avoids allocating another
   * {@code int[]}. Requires
   * {@code dimKey.length >= getDimensionNumbers().length}.
   * 
   * @param keyNum
   * @param dimKey
   */
  void keyNumToDimKey(long keyNum, int[] dimKey);

  long dimKeyToKeyNum(int[] dimKey);

  long dimKeyPrefixToKeyNum(int[] dimKeyPrefix);

  int keyNumToIndex(long keyNum);

  long indexToKeyNum(int index);

  /**
   * Gets an iterator over all keys and values of {@code this} whose associated
   * value is nonzero. The returned iterator is guaranteed to iterate over keys
   * with nonzero value, and may optionally iterate over keys whose value is
   * zero. The {@code KeyValue} returned by the iterator and its fields may be
   * modified later by the iterator, and must be copied in order to retain their
   * value.
   * 
   * @return
   */
  Iterator<KeyValue> keyValueIterator();

  /**
   * Gets an iterator over the subset of keys in {@code this} that begin with
   * {@code keyPrefix}. That is, the first {@code keyPrefix.length} dimensions
   * of each returned {@code KeyValue} are equal to their corresponding values
   * in {@code keyPrefix}. The returned iterator may optionally iterate over
   * keys whose value is 0.
   * 
   * @param keyPrefix
   * @return
   */
  Iterator<KeyValue> keyValuePrefixIterator(int[] keyPrefix);

  /**
   * Gets the Frobenius norm of this tensor, which is the square root of the sum
   * of each value squares of all values.
   * 
   * @return
   */
  double getL2Norm();

  /**
   * The key of a tensor and its corresponding value. For efficiency reasons,
   * <b>{@code KeyValue}s are mutable</b>. For example, iterators over tensors
   * typically return the same {@code KeyValue} object after updating both the
   * key and value field.
   * 
   * @author jayantk
   */
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

    protected void setKey(int[] key) {
      this.key = key;
    }

    public double getValue() {
      return value;
    }

    protected void setValue(double value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return "<" + Arrays.toString(key) + " : " + value + ">";
    }
  }
}
