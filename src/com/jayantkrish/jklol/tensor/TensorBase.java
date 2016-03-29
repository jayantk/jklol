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
public interface TensorBase extends TensorHash {

  int[] getDimensionNumbers();

  /**
   * Gets the size of each of this tensor's dimensions. Valid keys for this
   * tensor are (elementwise) between 0 and the returned values.
   * 
   * @return
   */
  int[] getDimensionSizes();

  /**
   * Gets an array whose vector product with a {@code dimKey} constructs its
   * corresponding {@code keyNum}. This method should only be used when the
   * offsets themselves are required -- in other cases, use
   * {@link #dimKeyToKeyNum(int[])}.
   * 
   * @return
   */
  long[] getDimensionOffsets();

  int numDimensions();

  /**
   * Gets the size of this tensor. For sparse tensors, this is the number of
   * nonzero values; for dense tensors, this is the total number of values.
   * Indexes from {@code 0} to {@code size() - 1} are valid for methods like
   * {@link #getByIndex(int)}.
   * 
   * @return
   */
  int size();

  /**
   * A keyNum {@code k} is valid for {@code this} if and only if
   * {@code 0 <= k < this.maxKeyNum()}.
   * 
   * @return
   */
  public long getMaxKeyNum();

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
   * Gets the {@code dimIndex}'th element of the dimension
   * key corresponding to keyNum.
   * 
   * @param keyNum
   * @param dimIndex
   * @return
   */
  int keyNumToPartialDimKey(long keyNum, int dimIndex);

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
  
  int indexToPartialDimKey(int index, int dimIndex);

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
   * of the squares of all values.
   * 
   * @return
   */
  double getL2Norm();

  /**
   * Gets the trace, or sum of all entries.
   * 
   * @return
   */
  double getTrace();

  /**
   * Gets the keynums of the {@code n} largest values in this tensor. If this
   * tensor contains fewer than {@code n} nonzero values, fewer than {@code n}
   * indexes may be returned. The keynums are returned in descending order by
   * their corresponding value, i.e., the 0th element of the returned array
   * points to the largest value in {@code this}.
   * 
   * @return
   */
  public long[] getLargestValues(int n);

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
