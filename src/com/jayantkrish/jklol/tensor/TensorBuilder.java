package com.jayantkrish.jklol.tensor;

/**
 * Mutable tensors.
 * 
 * @author jayant
 */
public interface TensorBuilder extends TensorBase {

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

  void incrementEntry(double amount, int... key);

  void multiply(TensorBase other);

  void multiply(double amount);

  void multiplyEntry(double amount, int... key);

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
