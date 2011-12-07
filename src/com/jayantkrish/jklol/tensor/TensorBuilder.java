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

  void increment(TensorBase other);

  void increment(double amount);

  void incrementWithMultiplier(TensorBase other, double multiplier);
  
  void incrementEntry(double amount, int... key);

  void multiply(TensorBase other);

  void multiply(double amount);
  
  void multiplyEntry(double amount, int... key);

  Tensor build();
}
