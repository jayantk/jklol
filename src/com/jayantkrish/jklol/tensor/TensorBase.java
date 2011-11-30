package com.jayantkrish.jklol.tensor;

import java.util.Iterator;

/**
 * Common methods shared by {@code Tensor}s and {@code TensorBuilder}s. These
 * methods define a common access mechanism across both mutable and immutable
 * variants of tensors.
 * 
 * @author jayant
 */
public interface TensorBase {

  int[] getDimensionNumbers();
  
  int[] getDimensionSizes();
  
  int size(); 
  
  double get(int[] key);
  
  Iterator<int[]> keyIterator();
}
