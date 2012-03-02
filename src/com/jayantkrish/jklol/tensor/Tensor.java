package com.jayantkrish.jklol.tensor;

import java.util.Collection;
import java.util.Map;

/**
 * Tensors are immutable. All operations on {@code Tensor}s return new objects,
 * leaving the originals unchanged.
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

  Tensor elementwiseLog();

  Tensor elementwiseExp();

  Tensor sumOutDimensions(Collection<Integer> dimensionsToEliminate);

  Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate);

  Tensor relabelDimensions(int[] newDimensions);

  Tensor relabelDimensions(Map<Integer, Integer> relabeling);

  /**
   * Gets the first index in {@code this} whose corresponding keyNum is >= than
   * {@code keyNum}. This method is intended for advanced use only.
   * 
   * @param keyNum
   * @return
   */
  public int getNearestIndex(long keyNum);

  /**
   * Gets an array of the values stored in this tensor. Values are addressable
   * by tensor indexes. This method is intended for advanced use only,
   * specifically for sections of code which must be extremely efficient. In
   * most cases, the other math operations in this file are more suitable.
   * 
   * Elements of the returned array must not be modified by the caller.
   * 
   * @return
   */
  double[] getValues();
}
