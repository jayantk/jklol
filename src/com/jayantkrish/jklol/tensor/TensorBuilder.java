package com.jayantkrish.jklol.tensor;

import java.io.Serializable;

/**
 * Interface for constructing tensors that behaves like a mutable tensor.
 * 
 * @author jayant
 */
public interface TensorBuilder extends TensorBase, Serializable {

  /**
   * Sets a value in this tensor, essentially performing
   * {@code this[key] = value}. {@code key} contains a value index for each
   * dimension, in the same order as passed to the constructor. This method
   * overwrites any previously stored value for {@code key}.
   * 
   * @param key
   * @param value
   */
  void put(int[] key, double value);

  void putByKeyNum(long keyNum, double value);

  void increment(TensorBase other);

  void increment(double amount);

  void incrementWithMultiplier(TensorBase other, double multiplier);
  
  void incrementOuterProductWithMultiplier(Tensor leftTensor, Tensor rightTensor,
      double multiplier);

  /**
   * Increments each value in {@code this} by other squared.
   *  
   * @param other
   */
  void incrementSquare(TensorBase other, double multiplier);

  void incrementEntry(double amount, int... key);
  
  void incrementEntryByKeyNum(double amount, long keyNum);

  void multiply(TensorBase other);

  void multiply(double amount);

  void multiplyEntry(double amount, int... key);
  
  void multiplyEntryByKeyNum(double amount, long keyNum);
  
  /**
   * Applies the soft threshold operator to each element in this. The soft
   * threshold operator {@code f} applied to an element {@code x} is defined as:
   * 
   * <ul>
   * <li>{@code f(x) = 0} if {@code |x| <= threshold}.
   * <li>{@code f(x) = x - (sign(x) * threshold)} if {@code |x| >= threshold}
   * </ul>
   * 
   * In words, this operator zeros out elements that are within
   * {@code threshold} of zero, then shrinks all other elements toward zero.
   * 
   * @param threshold
   */
  void softThreshold(double threshold);

  /**
   * Finds keys in this whose value is {@code >= threshold}. The value of each
   * such key is set to 1, and the value for all other keys is set to 0.
   *
   * @param threshold
   * @return
   */
  void findEntriesLargerThan(double threshold);

  /**
   * Gets the sum of the elementwise product of {@code this} and {@code other}.
   * Requires {@code this} and {@code other} to have identical dimension numbers
   * and sizes.
   * 
   * @param other
   * @return
   */
  double innerProduct(TensorBase other);

  /**
   * Exponentiates the values in this tensor, setting every value to {@code e ^
   * (current value)}. This method will destroy sparsity.
   */
  void exp();

  Tensor build();

  /**
   * Creates a {@code Tensor} from the keys and values stored in this builder.
   * This method is a less safe, but faster version of {@link #build()}. Unlike
   * {@code build()}, this method may not create a copy of the keys and values
   * in {@code this} to store in tensor. Consequently, mutating this
   * {@code TensorBuilder} may affect the key/value pairs stored in the returned
   * Tensor.
   * 
   * @return
   */
  Tensor buildNoCopy();

  /**
   * Gets a copy of this {@code TensorBuilder}. Mutating the returned builder
   * will not affect the values in {@code this}. This method preserves the type
   * of the builder (essentially, whether it is sparse or dense), so it is
   * preferred over copy constructors.
   * 
   * @return
   */
  TensorBuilder getCopy();
}
