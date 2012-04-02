package com.jayantkrish.jklol.tensor;

import java.util.Collection;
import java.util.Map;

import com.jayantkrish.jklol.tensor.TensorProtos.TensorProto;

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

  /**
   * Computes e to the power of each element in this tensor. This operation
   * applies {@code Math.exp} to every element of this and returns the result in
   * a new tensor.
   * 
   * @return
   */
  Tensor elementwiseExp();

  Tensor sumOutDimensions(Collection<Integer> dimensionsToEliminate);

  Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate);

  Tensor relabelDimensions(int[] newDimensions);

  Tensor relabelDimensions(Map<Integer, Integer> relabeling);

  /**
   * Replaces the array of values backing this tensor with {@code values}.
   * Intended for advanced use only.
   * 
   * @param values
   * @return
   */
  Tensor replaceValues(double[] values);

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

  /**
   * Serializes {@code this} tensor into a protocol buffer. The returned
   * protocol buffer can be deserialized (to reconstruct a copy of this) using
   * {@link Tensors#fromProto()}.
   * 
   * @return
   */
  TensorProto toProto();
}
