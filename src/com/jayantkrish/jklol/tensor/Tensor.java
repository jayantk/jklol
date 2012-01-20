package com.jayantkrish.jklol.tensor;

import java.util.Collection;
import java.util.Map;

/**
 * Tensors are immutable. All operations on {@code Tensor}s, such as
 * 
 * @author jayant
 */
public interface Tensor extends TensorBase {

  /**
   * Selects a lower-dimensional subset of {@code this} by fixing the
   * {@code keys} of {@code dimensionNumbers}. The value of a key in the
   * returned tensor is equal to the value in {@code this} of key augmented with
   * {@code keyValues} (on the appropriate dimensions).
   * 
   * @param dimensionNumbers
   * @param keys
   * @return
   */
  Tensor slice(int[] dimensionNumbers, int[] keys);

  Tensor elementwiseProduct(Tensor other);

  Tensor elementwiseAddition(Tensor other);

  Tensor elementwiseMaximum(Tensor other);

  Tensor elementwiseInverse();

  Tensor sumOutDimensions(Collection<Integer> dimensionsToEliminate);

  Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate);

  Tensor relabelDimensions(int[] newDimensions);

  Tensor relabelDimensions(Map<Integer, Integer> relabeling);
}
