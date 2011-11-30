package com.jayantkrish.jklol.tensor;

import java.util.Map;
import java.util.Set;

/**
 * Tensors are immutable. All operations on {@code Tensor}s, such as  
 * @author jayant
 */
public interface Tensor extends TensorBase {

  Tensor elementwiseProduct(Tensor other);
  
  Tensor elementwiseAddition(Tensor other);
  
  Tensor elementwiseMaximum(Tensor other);
  
  Tensor elementwiseInverse();
  
  Tensor sumOutDimensions(Set<Integer> dimensionsToEliminate);
  
  Tensor maxOutDimensions(Set<Integer> dimensionsToEliminate);
  
  Tensor relabelDimensions(int[] newDimensions);
  
  Tensor relabelDimensions(Map<Integer, Integer> relabeling);
}
