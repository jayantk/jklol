package com.jayantkrish.jklol.tensor;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import com.jayantkrish.jklol.tensor.TensorProtos.TensorProto;

/**
 * Tensors are immutable. All operations on {@code Tensor}s return new objects,
 * leaving the originals unchanged.
 * 
 * @author jayant
 */
public interface Tensor extends TensorBase, Serializable {

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

  Tensor elementwiseProduct(Collection<Tensor> others);

  Tensor elementwiseProduct(double value);

  Tensor outerProduct(Tensor other);

  /**
   * Performs elementwise addition of {@code this} and {@code other} and returns
   * a new {@code SparseTensor} containing the result. The value of key {code k}
   * in the returned table is {@code this.get(k) + other.get(k)}. Requires
   * {@code other} and {@code this} to contain the same dimensions.
   * 
   * @param other
   * @return
   */
  Tensor elementwiseAddition(Tensor other);
  
  /**
   * Adds {@code value} to every value in {@code this} and returns the result.
   *  
   * @param value
   * @return
   */
  Tensor elementwiseAddition(double value);

  /**
   * Computes the elementwise maximum of {@code this} and {@code other},
   * returning a new {@code SparseTensor} containing the result. The value of
   * key {code k} in the returned table is
   * {@code Math.max(this.get(k), other.get(k))} . Requires {@code other} and
   * {@code this} to contain the same dimensions.
   * 
   * @param other
   * @return
   */
  Tensor elementwiseMaximum(Tensor other);

  /**
   * Returns the elementwise multiplicative inverse of {@code this}. For all
   * keys {@code k} in {@code this}, {@code inverse.get(k) * this.get(k) == 1}.
   * For all keys not in {@code this}, {@code inverse.get(k) == 0}.
   * 
   * @return
   */
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

  /**
   * Applies {@code op} to the value of each key in {@code this}, including keys
   * with zero values.
   * 
   * @param op
   * @return
   */
  // Tensor elementwiseOp(Function<Double, Double> op);

  /**
   * Applies {@code op} to the value of each key in {@code this}, ignoring any
   * keys with zero values.
   * 
   * @param op
   * @return
   */
  // Tensor sparseElementwiseOp(Function<Double, Double> op);

  /**
   * Sums out {@code dimensionsToEliminate}, returning a lower-dimensional
   * tensor containing the remaining dimensions. The value of key {@code k} in
   * the returned tensor is the sum over all keys in {@code this} which are
   * supersets of {@code k}.
   * 
   * @param dimensionsToEliminate
   * @return
   */
  Tensor sumOutDimensions(Collection<Integer> dimensionsToEliminate);

  /**
   * Maximizes out {@code dimensionsToEliminate}, returning a lower-dimensional
   * tensor containing the remaining dimensions. The value of key {@code k} in
   * the returned tensor is the maximum over all keys in {@code this} which are
   * supersets of {@code k}.
   * 
   * @param dimensionsToEliminate
   * @return
   */
  Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate);

  /**
   * Same as {@link #maxOutDimensions(Collection)}, except additionally returns
   * the {@code keyNums} which were used in the construction of the returned
   * tensor. These are returned in {@code backpointers}, which acts like a map
   * from keys in the returned tensor to keys in {@code this}.
   * 
   * @param dimensionsToEliminate
   * @param backpointers
   * @return
   */
  Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate, Backpointers backpointers);

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
