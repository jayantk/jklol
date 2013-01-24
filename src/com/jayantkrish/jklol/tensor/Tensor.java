package com.jayantkrish.jklol.tensor;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * Tensors are generalizations of matrices that have any number of key
 * dimensions (known as modes). This interface represents mappings
 * from multidimensional keys (represented as {@code int[]}) to
 * values. Tensors support mathematical operations, such as addition
 * and multiplication.
 * 
 * <p>
 * The values of a tensor are accessible in three ways: using
 * {@code int[]} keys (known as "dim keys"), keynums, and indexes. Dim
 * keys are arrays with the same dimensionality as this tensor, where
 * each entry represents the key for a particular dimension. Keynums
 * are simply numeric encodings of dim keys, and are convertible to
 * dimkeys 1-to-1. However, indexes only represent a subset of the
 * keys in a tensor; all non-zero values are accessible by index.
 * Indexes may be converted into keynums, but not all keynums may be
 * mapped to indexes.
 * 
 * <p>
 * Tensors are immutable. All operations on {@code Tensor}s return new
 * objects, leaving the originals unchanged.
 * 
 * @author jayant
 */
public interface Tensor extends TensorBase, Serializable {

  /**
   * Selects a lower-dimensional subset of {@code this} by fixing the
   * {@code keys} of {@code dimensionNumbers}. The value of a key in
   * the returned tensor is equal to the value in {@code this} of key
   * augmented with {@code keys} (along the appropriate dimensions).
   * 
   * @param dimensionNumbers
   * @param keys
   * @return
   */
  Tensor slice(int[] dimensionNumbers, int[] keys);

  /**
   * Selects a subset of the keys in this tensor, as given by
   * {@code indicatorTensor}. The primary use of this method is to
   * sparsify a tensor to make future lookups more efficient by
   * discarding unnecessary keys.
   * <p>
   * All keys {@code k} in the returned tensor have the same value as
   * in {@code this}, if (a subset of) {@code k} is in
   * {@code indicatorTensor}. If {@code k} is not in
   * {@code indicatorTensor}, any value may be returned. All values in
   * {@code indicatorTensor} must be either 0 or 1.
   * 
   * @param indicatorTensor
   * @return
   */
  Tensor retainKeys(Tensor indicatorTensor);

  /**
   * Returns the elementwise product of this with {@code other}.
   * {@code other} must contain a subset of the dimensions of
   * {@code this}, and the returned tensor will have the same
   * dimensions as {@code this}. Each key in the returned tensor's
   * value will be equal to {@code this.get(k) * other.get(k)} .
   * 
   * @param other tensor to multiply with {@code this}.
   * @return the elementwise product of {@code this} with
   * {@code other}.
   */
  Tensor elementwiseProduct(Tensor other);

  /**
   * Elementwise multiplies this tensor with each tensor in
   * {@code others} and returns the result. The effect of this method
   * is identical to repeatedly applying
   * {@link #elementwiseProduct(Tensor)}, but may be more efficient.
   * 
   * @param others tensors to multiply with {@code this}
   * @return the elementwise product of {@code this} with
   * {@code others}.
   */
  Tensor elementwiseProduct(Collection<Tensor> others);

  /**
   * Elementwise multiplies each value in {@code this} by
   * {@code value}.
   * 
   * @param value value to multiply by
   * @return tensor equal to {@code this} with each value multiplied
   * by {@code value}.
   */
  Tensor elementwiseProduct(double value);

  /**
   * The tensor inner product, which is analogous to the standard
   * matrix product. This method elementwise multiplies {@code this}
   * and {@code other}, then sums out all dimensions in {@code other}.
   * The returned tensor has the dimensions of {@code this} minus the
   * dimensions of {@code other}.
   * 
   * @param other
   * @return
   */
  Tensor innerProduct(Tensor other);

  /**
   * The outer product. {@code other} and {@code this} must have
   * disjoint sets of dimensions, and the returned tensor will have
   * the union of both sets of dimensions. The value of key {@code k}
   * in the returned tensor is equal to
   * {@code this.get(k) * other.get(k)}.
   */
  Tensor outerProduct(Tensor other);

  /**
   * Performs elementwise addition of {@code this} and {@code other}
   * and returns a new {@code SparseTensor} containing the result. The
   * value of key {code k} in the returned table is
   * {@code this.get(k) + other.get(k)}. Requires {@code other} and
   * {@code this} to contain the same dimensions.
   * 
   * @param other
   * @return
   */
  Tensor elementwiseAddition(Tensor other);

  /**
   * Adds {@code value} to every value in {@code this} and returns the
   * result.
   * 
   * @param value
   * @return
   */
  Tensor elementwiseAddition(double value);

  /**
   * Computes the elementwise maximum of {@code this} and
   * {@code other}, returning a new {@code SparseTensor} containing
   * the result. The value of key {code k} in the returned table is
   * {@code Math.max(this.get(k), other.get(k))} . Requires
   * {@code other} and {@code this} to contain the same dimensions.
   * 
   * @param other
   * @return
   */
  Tensor elementwiseMaximum(Tensor other);

  /**
   * Returns the elementwise multiplicative inverse of {@code this}.
   * For all keys {@code k} in {@code this},
   * {@code inverse.get(k) * this.get(k) == 1}. For all keys not in
   * {@code this}, {@code inverse.get(k) == 0}.
   * <p>
   * The multiplicative inverse of 0 is also 0.
   * 
   * @return
   */
  Tensor elementwiseInverse();

  /**
   * Takes the square root of all elements in this. Note that negative
   * valued elements will be mapped to NaN.
   * 
   * @return
   */
  Tensor elementwiseSqrt();

  /**
   * Takes the natural log of each value in {@code this}. Note that
   * zero values will be mapped to -Infinity.
   * 
   * @return tensor whose values are the logarithm of {@code this}
   * tensor's values.
   */
  Tensor elementwiseLog();

  /**
   * Computes {@code e} to the power of each element in this tensor.
   * This operation applies {@code Math.exp} to every element of this
   * and returns the result in a new tensor.
   * 
   * @return
   */
  Tensor elementwiseExp();

  /**
   * Applies the soft threshold operator to each element in this. The
   * soft threshold operator {@code f} applied to an element {@code x}
   * is defined as:
   * 
   * <ul>
   * <li>{@code f(x) = 0} if {@code |x| <= threshold}.
   * <li>{@code f(x) = x - (sign(x) * threshold)} if
   * {@code |x| >= threshold}
   * </ul>
   * 
   * In words, this operator zeros out elements that are within
   * {@code threshold} of zero, then shrinks all other elements toward
   * zero.
   * 
   * @param threshold
   */
  Tensor softThreshold(double threshold);

  /**
   * Applies {@code op} to the value of each key in {@code this},
   * including keys with zero values.
   * 
   * @param op
   * @return
   */
  // Tensor elementwiseOp(Function<Double, Double> op);

  /**
   * Applies {@code op} to the value of each key in {@code this},
   * ignoring any keys with zero values.
   * 
   * @param op
   * @return
   */
  // Tensor sparseElementwiseOp(Function<Double, Double> op);

  /**
   * Sums out {@code dimensionsToEliminate}, returning a
   * lower-dimensional tensor containing the remaining dimensions. The
   * value of key {@code k} in the returned tensor is the sum over all
   * keys in {@code this} which are supersets of {@code k}.
   * 
   * @param dimensionsToEliminate
   * @return
   */
  Tensor sumOutDimensions(Collection<Integer> dimensionsToEliminate);

  /**
   * Same as {@link sumOutDimensions(Collection)}.
   */
  Tensor sumOutDimensions(int[] dimensionsToEliminate);

  /**
   * Sums out {@code dimensionsToEliminate} using the log addition
   * rule, returning a lower-dimensional tensor containing the
   * remaining dimensions. Log addition for two numbers {@code a, b}
   * returns the number {@code c} such that {@code e^c = e^a + e^b}.
   * The value of key {@code k} in the returned tensor the value of
   * this formula applied to all keys in {@code this} which are
   * supersets of {@code k}.
   * <p>
   * This method is useful for computing partition functions using log
   * probabilities, since log addition is more numerically stable than
   * exponentiating and adding in that space.
   * 
   * @param dimensionsToEliminate
   * @return
   */
  Tensor logSumOutDimensions(Collection<Integer> dimensionsToEliminate);

  /**
   * Same as {@link #logSumOutDimensions(Collection)}.
   * 
   * @param dimensionsToEliminate
   * @return
   */
  Tensor logSumOutDimensions(int[] dimensionsToEliminate);

  /**
   * Maximizes out {@code dimensionsToEliminate}, returning a
   * lower-dimensional tensor containing the remaining dimensions. The
   * value of key {@code k} in the returned tensor is the maximum over
   * all keys in {@code this} which are supersets of {@code k}.
   * 
   * @param dimensionsToEliminate
   * @return
   */
  Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate);

  /**
   * Same as {@link #maxOutDimensions(Collection)}.
   */
  Tensor maxOutDimensions(int[] dimensionsToEliminate);

  /**
   * Same as {@link #maxOutDimensions(Collection)}, except
   * additionally returns the {@code keyNums} which were used in the
   * construction of the returned tensor. These are returned in
   * {@code backpointers}, which acts like a map from keys in the
   * returned tensor to keys in {@code this}.
   * 
   * @param dimensionsToEliminate
   * @param backpointers
   * @return
   */
  Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate, Backpointers backpointers);

  /**
   * Relabels the dimensions of this tensor to {@code newDimensions}.
   * The i'th dimension of {@code this} is relabeled to the i'th
   * element in {@code newDimensions}. If the resulting relabeled
   * dimensions are not in sorted order, they will be reordered and
   * the keys of the tensor will be appropriately relabeled.
   * <p>
   * Expects {@code newDimensions} to have length equal to the number
   * of dimensions in this tensor.
   * 
   * @param newDimensions target dimensions to relabel {@code this}
   * tensor's dimensions to.
   * @return copy of {@code this} with its dimensions relabeled to
   * match {@code newDimensions}.
   */
  Tensor relabelDimensions(int[] newDimensions);

  /**
   * Same as {@link relabelDimensions(int[])} with a different format
   * for the relabeling. {@code relabeling} maps each dimension in
   * {@code this} to a new dimension which should replace the existing
   * dimension.
   * 
   * @param relabeling mapping from dimensions of {@code this} to new
   * dimensions.
   * @return copy of {@code this} with its dimensions relabeled
   * according to {@code relabeling}.
   */
  Tensor relabelDimensions(Map<Integer, Integer> relabeling);

  /**
   * Replaces the array of values backing this tensor with
   * {@code values}. Intended for advanced use only.
   * 
   * @param values
   * @return
   */
  Tensor replaceValues(double[] values);

  /**
   * Gets the first index in {@code this} whose corresponding keyNum
   * is >= than {@code keyNum}. This method is intended for advanced
   * use only.
   * 
   * @param keyNum
   * @return
   */
  int getNearestIndex(long keyNum);

  /**
   * Gets an array of the values stored in this tensor. Values are
   * addressable by tensor indexes. This method is intended for
   * advanced use only, specifically for sections of code which must
   * be extremely efficient. In most cases, the other math operations
   * in this file are more suitable.
   * 
   * Elements of the returned array must not be modified by the
   * caller.
   * 
   * @return
   */
  double[] getValues();
}
