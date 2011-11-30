package com.jayantkrish.jklol.tensor;

/**
 * Mutable tensors.
 * 
 * @author jayant
 */
public interface TensorBuilder extends TensorBase {

  void put(int[] key, double value);
  
  void increment(TensorBase other);
  
  void multiply(TensorBase other);
  
  Tensor build();
}
