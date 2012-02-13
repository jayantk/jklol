package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

/**
 * A SparseTensor sparsely stores a mapping from int[] to double. This class
 * represents a sparse tensor, where all values are presumed to be 0 unless
 * otherwise specified.
 * 
 * SparseTensors are immutable.
 */
public class SparseTensor extends AbstractTensorBase implements Tensor {

  private final long[] keyNums;
  private final double[] values;

  public SparseTensor(int[] dimensionNums, int[] dimensionSizes, long[] keyNums, double[] values) {
    super(dimensionNums, dimensionSizes);
    this.keyNums = Preconditions.checkNotNull(keyNums);
    this.values = Preconditions.checkNotNull(values);

    Preconditions.checkArgument(Ordering.natural().isOrdered(Ints.asList(dimensionNums)));
    Preconditions.checkArgument(keyNums.length == values.length);
  }

  // ////////////////////////////////////////////////////////////////////
  // Inherited from TensorBase
  // ////////////////////////////////////////////////////////////////////

  /**
   * Gets the number of keys stored in {@code this}.
   * 
   * @return
   */
  @Override
  public int size() {
    return values.length;
  }

  /**
   * Returns {@code true} if {@code key} has a value in this. Equivalent to
   * {@code this.get(key) != null}.
   * 
   * @param key
   * @return
   */
  public boolean containsKey(int[] key) {
    return keyNumToIndex(dimKeyToKeyNum(key)) != -1;
  }

  /**
   * Get the value associated with a variable assignment. Returns {@code 0.0} if
   * no value is associated with {@code key}.
   */
  @Override
  public double getByDimKey(int... key) {
    return getByIndex(keyNumToIndex(dimKeyToKeyNum(key)));
  }

  @Override
  public double getByIndex(int index) {
    if (index == -1) {
      return 0.0;
    }
    return values[index];
  }

  @Override
  public long indexToKeyNum(int index) {
    return keyNums[index];
  }

  @Override
  public int keyNumToIndex(long keyNum) {
    int possibleIndex = Arrays.binarySearch(keyNums, keyNum);
    return possibleIndex >= 0 ? possibleIndex : -1;
  }

  /**
   * Gets the first index in {@code this} whose corresponding keyNum is >= than
   * {@code keyNum}.
   * 
   * @param keyNum
   * @return
   */
  private int getFirstIndexAboveKeyNum(long keyNum) {
    int index = Arrays.binarySearch(keyNums, keyNum);
    if (index < 0) {
      index = (-1 * index) - 1;
    }
    return index;
  }

  /**
   * Returns an iterator over all assignments (keys) in this table.
   */
  @Override
  public Iterator<int[]> keyValueIterator() {
    return new SparseKeyIterator(keyNums, 0, keyNums.length, this);
  }

  @Override
  public Iterator<int[]> keyPrefixIterator(int[] keyPrefix) {
    if (keyPrefix.length == 0) { return keyValueIterator(); }
    
    long startKeyNum = dimKeyPrefixToKeyNum(keyPrefix);
    long endKeyNum = startKeyNum + indexOffsets[keyPrefix.length - 1];
    return new SparseKeyIterator(keyNums, getFirstIndexAboveKeyNum(startKeyNum),
        getFirstIndexAboveKeyNum(endKeyNum), this);
  }

  @Override
  public double getL2Norm() {
    double sumSquared = 0.0;
    for (int i = 0; i < size(); i++) {
      sumSquared += values[i] * values[i];
    }
    return Math.sqrt(sumSquared);
  }

  // /////////////////////////////////////////////////////////////////
  // Inherited from Tensor
  // /////////////////////////////////////////////////////////////////

  @Override
  public SparseTensor slice(int[] dimensionNumbers, int[] key) {
    if (dimensionNumbers.length == 0) {
      return this;
    }
    // Check for an efficient case, where the dimensions are the first elements
    // of this.
    if (Arrays.equals(Arrays.copyOf(getDimensionNumbers(), dimensionNumbers.length),
        dimensionNumbers)) {
      long minKeyInt = 0;
      for (int i = 0; i < dimensionNumbers.length; i++) {
        minKeyInt += indexOffsets[i] * key[i];
      }
      long maxKeyInt = minKeyInt + indexOffsets[dimensionNumbers.length - 1];

      int startIndex = getFirstIndexAboveKeyNum(minKeyInt);
      int endIndex = getFirstIndexAboveKeyNum(maxKeyInt);

      long[] newKeyInts = Arrays.copyOfRange(keyNums, startIndex, endIndex);
      for (int i = 0; i < newKeyInts.length; i++) {
        newKeyInts[i] -= minKeyInt;
      }

      int[] newDimensionNumbers = Arrays.copyOfRange(getDimensionNumbers(), dimensionNumbers.length, getDimensionNumbers().length);
      int[] newDimensionSizes = Arrays.copyOfRange(getDimensionSizes(), dimensionNumbers.length, getDimensionSizes().length);
      double[] newValues = Arrays.copyOfRange(values, startIndex, endIndex);
      return new SparseTensor(newDimensionNumbers, newDimensionSizes, newKeyInts, newValues);
    }

    // TODO(jayantk): This is an extremely naive implementation of slice.
    // Figure out the appropriate sizes for the subset of dimensions.
    int[] dimensionSizes = new int[dimensionNumbers.length];
    for (int i = 0; i < dimensionNumbers.length; i++) {
      int dimIndex = getDimensionIndex(dimensionNumbers[i]);
      Preconditions.checkArgument(dimIndex >= 0);
      dimensionSizes[i] = getDimensionSizes()[dimIndex];
    }
    SparseTensorBuilder builder = new SparseTensorBuilder(dimensionNumbers, dimensionSizes);
    builder.put(key, 1.0);
    return elementwiseProduct(builder.build()).sumOutDimensions(Ints.asList(dimensionNumbers));
  }

  /**
   * Elementwise multiplies {@code this} and {@code other}, returning the result
   * as a newly allocated object. {@code other}'s dimensions must be a subset of
   * the dimensions of {@code this}. If {@code other} contains fewer dimensions
   * than {@code this}, it is treated as if it were replicated across all
   * missing dimensions. (In Matlab terms, this is equivalent to doing a
   * repmat() across the missing dimensions of {@code other}, then performing
   * elementwise multiplication).
   * 
   * @param other
   * @return
   */
  @Override
  public SparseTensor elementwiseProduct(Tensor other) {
    int[] dimensionNums = getDimensionNumbers();
    Set<Integer> myDims = Sets.newHashSet(Ints.asList(dimensionNums));
    Preconditions.checkArgument(myDims.containsAll(Ints.asList(other.getDimensionNumbers())));

    // Permute the dimensions of this so that the dimension of other are
    // left-aligned, multiply, then reverse the permutation.
    BiMap<Integer, Integer> permutation = HashBiMap.create();
    int numDimsDifferent = 0;
    int otherInd = 0;
    for (int i = 0; i < dimensionNums.length; i++) {
      if (otherInd < other.getDimensionNumbers().length && other.getDimensionNumbers()[otherInd] > dimensionNums[i]) {
        permutation.put(dimensionNums[i], Integer.MAX_VALUE - numDimsDifferent);
        numDimsDifferent++;
      } else {
        permutation.put(dimensionNums[i], dimensionNums[i]);
        otherInd++;
      }
    }

    SparseTensor result = elementwiseMultiplyLeftAligned(this.relabelDimensions(permutation), other);
    return result.relabelDimensions(permutation.inverse());
  }

  /**
   * Elementwise multiplies two tensors. {@code big} must contain a superset of
   * the dimensions of {@code small}, and the dimensions must be arranged such
   * that all dimensions shared by {@code big} and {@code small} are aligned on
   * the left side of their respective {@code keyNums} arrays.
   * 
   * @param big
   * @param small
   * @return
   */
  protected static final SparseTensor elementwiseMultiplyLeftAligned(SparseTensor big, Tensor small) {
    // The result tensor is no larger than the larger (superset of dimensions)
    // tensor.
    long[] resultKeyInts = new long[big.size()];
    double[] resultValues = new double[big.size()];
    // How many result values have been filled so far.
    int resultInd = 0;

    // Current positions in each tensor's key/value array.
    int myInd = 0;
    int otherInd = advanceToNonzero(-1, small);

    int smallIndexMultiplier = 1;
    for (int i = big.numDimensions() - 1; i >= small.numDimensions(); i--) {
      smallIndexMultiplier *= big.getDimensionSizes()[i];
    }

    for (myInd = 0; myInd < big.size(); myInd = advance(myInd, big.keyNums, otherInd, small, smallIndexMultiplier)) {
      // Advance otherInd until other's outcome is >= our outcome.
      while (otherInd < small.size() &&
          big.indexToKeyNum(myInd) / smallIndexMultiplier > small.indexToKeyNum(otherInd)) {
        otherInd = advanceToNonzero(otherInd, small);
      }

      if (otherInd < small.size() &&
          big.indexToKeyNum(myInd) / smallIndexMultiplier == small.indexToKeyNum(otherInd)) {
        resultKeyInts[resultInd] = big.indexToKeyNum(myInd);
        resultValues[resultInd] = big.values[myInd] * small.getByIndex(otherInd);
        resultInd++;
      }
    }
    return resizeIntoTable(big.getDimensionNumbers(), big.getDimensionSizes(),
        resultKeyInts, resultValues, resultInd);
  }

  private static final int advance(int myInd, long[] myKeyInts, int otherInd, Tensor other,
      int smallKeyIntMultiplier) {
    // This algorithm advances myInd into a block of outcomes where the first
    // coordinate of this tensor's outcomes matches the first coordinate of the
    // other
    // tensor's outcome.
    if (otherInd >= other.size()) {
      return myKeyInts.length;
    } else if (other.numDimensions() == 0) {
      return myInd + 1;
    } else if (myKeyInts[myInd] / smallKeyIntMultiplier == other.indexToKeyNum(otherInd)) {
      // We're within a block of outcomes of other that start with the same
      // coordinates as myOutcomes.
      return myInd + 1;
    } else {
      // Find a block of outcomes with the correct starting coordinates.
      return binarySearch(myKeyInts, other.indexToKeyNum(otherInd) * smallKeyIntMultiplier,
          myInd, myKeyInts.length);
    }
  }

  private static final int advanceToNonzero(int startInd, Tensor other) {
    int nextInd = startInd + 1;
    while (nextInd < other.size() && other.getByIndex(nextInd) == 0) {
      nextInd++;
    }
    return nextInd;
  }

  /**
   * Returns the first index of {@code value} in {@code array} that occurs
   * between {@code array[startInd]} and {@code array[endInd - 1]}.
   * 
   * @param array
   * @param value
   * @param startInd
   * @param endInd
   * @return
   */
  private static final int binarySearch(long[] array, long value, int startInd, int endInd) {
    if (startInd == endInd) {
      return startInd;
    }

    int cmpInd = (startInd + endInd) / 2;
    if (value > array[cmpInd]) {
      // Value comes after cmpInd in array.
      return binarySearch(array, value, cmpInd + 1, endInd);
    } else {
      // either array[cmpInd] equals value, or is less than value
      return binarySearch(array, value, startInd, cmpInd);
    }
  }

  /**
   * Performs elementwise addition of {@code this} and {@code other} and returns
   * a new {@code SparseTensor} containing the result. The value of key {code k}
   * in the returned table is {@code this.get(k) + other.get(k)}. Requires
   * {@code other} and {@code this} to contain the same dimensions.
   * 
   * @param other
   * @return
   */
  @Override
  public SparseTensor elementwiseAddition(Tensor otherTensor) {
    return doElementwise(otherTensor, true);
  }

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
  @Override
  public SparseTensor elementwiseMaximum(Tensor otherTensor) {
    return doElementwise(otherTensor, false);
  }

  /**
   * Helper method for performing {@link #elementwiseAddition} and
   * {@link #elementwiseMaximum}. If {@code useSum == true} this adds the values
   * of equivalent outcomes; otherwise, it takes the maximum of the two
   * outcomes.
   */
  private SparseTensor doElementwise(Tensor other, boolean useSum) {
    // TODO(jayantk): This method could be generalized by taking a Function
    // instead of a boolean flag. However, it's unclear how this change
    // affects performance.
    Preconditions.checkArgument(Arrays.equals(getDimensionNumbers(), other.getDimensionNumbers()));
    long[] resultKeyInts = new long[size() + other.size()];
    double[] resultValues = new double[size() + other.size()];

    int resultInd = 0;
    int myInd = 0;
    int otherInd = 0;

    while (myInd < size() && otherInd < other.size()) {
      if (keyNums[myInd] < other.indexToKeyNum(otherInd)) {
        resultKeyInts[resultInd] = keyNums[myInd];
        resultValues[resultInd] = values[myInd];
        resultInd++;
        myInd++;
      } else if (keyNums[myInd] > other.indexToKeyNum(otherInd)) {
        resultKeyInts[resultInd] = other.indexToKeyNum(otherInd);
        resultValues[resultInd] = other.getByIndex(otherInd);
        resultInd++;
        otherInd++;
      } else {
        // Keys are equal.
        resultKeyInts[resultInd] = keyNums[myInd];
        if (useSum) {
          resultValues[resultInd] = values[myInd] + other.getByIndex(otherInd);
        } else {
          resultValues[resultInd] = Math.max(values[myInd], other.getByIndex(otherInd));
        }
        resultInd++;
        myInd++;
        otherInd++;
      }
    }

    // One of the two lists might not be done yet. Finish it off.
    for (; myInd < size(); myInd++) {
      resultKeyInts[resultInd] = keyNums[myInd];
      resultValues[resultInd] = values[myInd];
      resultInd++;
    }

    for (; otherInd < other.size(); otherInd++) {
      resultKeyInts[resultInd] = other.indexToKeyNum(otherInd);
      resultValues[resultInd] = other.getByIndex(otherInd);
      resultInd++;
    }

    return resizeIntoTable(getDimensionNumbers(), getDimensionSizes(), resultKeyInts, resultValues, resultInd);
  }

  /**
   * Returns the elementwise multiplicative inverse of {@code this}. For all
   * keys {@code k} in {@code this}, {@code inverse.get(k) * this.get(k) == 1}.
   * For all keys not in {@code this}, {@code inverse.get(k) == 0}.
   * 
   * @return
   */
  @Override
  public SparseTensor elementwiseInverse() {
    double[] newValues = new double[size()];
    for (int i = 0; i < size(); i++) {
      newValues[i] = 1.0 / values[i];
    }
    // We don't have to copy outcomes because this class is immutable, and it
    // treats both outcomes and values as immutable.
    return new SparseTensor(getDimensionNumbers(), getDimensionSizes(), keyNums, newValues);
  }

  /**
   * Sums out {@code dimensionsToEliminate}, returning a lower-dimensional
   * tensor containing the remaining dimensions. The value of key {@code k} in
   * the returned tensor is the sum over all keys in {@code this} which are
   * supersets of {@code k}.
   * 
   * @param dimensionsToEliminate
   * @return
   */
  @Override
  public SparseTensor sumOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return reduceDimensions(Sets.newHashSet(dimensionsToEliminate), true);
  }

  /**
   * Maximizes out {@code dimensionsToEliminate}, returning a lower-dimensional
   * tensor containing the remaining dimensions. The value of key {@code k} in
   * the returned tensor is the maximum over all keys in {@code this} which are
   * supersets of {@code k}.
   * 
   * @param dimensionsToEliminate
   * @return
   */
  @Override
  public SparseTensor maxOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return reduceDimensions(Sets.newHashSet(dimensionsToEliminate), false);
  }

  /**
   * Eliminates {@code dimensionsToEliminate}, either by summing or maximizing.
   * 
   * @param dimensionsToEliminate
   * @param useSum
   * @return
   */
  private SparseTensor reduceDimensions(Set<Integer> dimensionsToEliminate,
      boolean useSum) {
    // TODO(jayantk): This method can be generalized to support a generic reduce
    // operation (as a function), but it's unclear what the effect on
    // performance will be.

    // Rotate all of the dimensions which are being eliminated to the
    // end of the keyNums array.
    int[] newLabels = new int[numDimensions()];
    int[] newDimensions = new int[numDimensions()];
    int[] newDimensionSizes = new int[numDimensions()];
    int numEliminated = 0;
    int[] dimensionNums = getDimensionNumbers();
    int[] dimensionSizes = getDimensionSizes();
    for (int i = 0; i < dimensionNums.length; i++) {
      if (dimensionsToEliminate.contains(dimensionNums[i])) {
        // Dimension labels must be unique, hence numEliminated.
        newLabels[i] = Integer.MAX_VALUE - numEliminated;
        numEliminated++;
      } else {
        newLabels[i] = dimensionNums[i];
        newDimensions[i - numEliminated] = dimensionNums[i];
        newDimensionSizes[i - numEliminated] = dimensionSizes[i];
      }
    }
    // If none of the dimensions being eliminated are actually part of this
    // tensor,
    // then there's no need to do any more work.
    if (numEliminated == 0) {
      return this;
    }

    SparseTensor relabeled = relabelDimensions(newLabels);
    int resultNumDimensions = dimensionNums.length - numEliminated;

    // Get a number which we can divide each key by to map it to a key in the
    // reduced dimensional tensor.
    long keyNumDenominator = (resultNumDimensions > 0) ? relabeled.indexOffsets[resultNumDimensions - 1] :
        relabeled.indexOffsets[0] * relabeled.getDimensionSizes()[0];

    long[] resultKeyInts = new long[relabeled.values.length];
    double[] resultValues = new double[relabeled.values.length];
    int resultInd = 0;
    for (int i = 0; i < relabeled.values.length; i++) {
      if (i != 0 &&
          (relabeled.keyNums[i - 1] / keyNumDenominator) == (relabeled.keyNums[i] / keyNumDenominator)) {
        // This key maps to the same entry as the previous key.
        if (useSum) {
          resultValues[resultInd - 1] += relabeled.values[i];
        } else {
          resultValues[resultInd - 1] = Math.max(resultValues[resultInd - 1],
              relabeled.values[i]);
        }
      } else {
        resultKeyInts[resultInd] = relabeled.keyNums[i] / keyNumDenominator;
        resultValues[resultInd] = relabeled.values[i];
        resultInd++;
      }
    }

    return resizeIntoTable(Arrays.copyOf(newDimensions, resultNumDimensions),
        Arrays.copyOf(newDimensionSizes, resultNumDimensions),
        resultKeyInts, resultValues, resultInd);
  }

  /**
   * Relabels the dimensions of {@code this}. Each dimension is relabeled to its
   * value in {@code relabeling}. {@code relabeling} must contain a unique value
   * for each of {@code this.getDimensionNumbers()}.
   * 
   * @param relabeling
   * @see #relabelDimensions(int[])
   * @return
   */
  @Override
  public SparseTensor relabelDimensions(Map<Integer, Integer> relabeling) {
    int[] newDimensions = new int[numDimensions()];
    int[] dimensionNums = getDimensionNumbers();
    for (int i = 0; i < dimensionNums.length; i++) {
      newDimensions[i] = relabeling.get(dimensionNums[i]);
    }
    return relabelDimensions(newDimensions);
  }

  /**
   * Relabels the dimension numbers of {@code this} and returns the result.
   * {@code newDimensions.length} must equal
   * {@code this.getDimensionNumbers().length}. The {@code ith} entry in
   * {@code this.getDimensionNumbers()} is relabeled as {@code newDimensions[i]}
   * in the result.
   * 
   * @param newDimensions
   * @return
   */
  @Override
  public SparseTensor relabelDimensions(int[] newDimensions) {
    Preconditions.checkArgument(newDimensions.length == numDimensions());
    if (Ordering.natural().isOrdered(Ints.asList(newDimensions))) {
      // If the new dimension labels are in sorted order, then we don't have to
      // resort the outcome and value arrays. This is a big efficiency win if it
      // happens. Note that keyNums and values are (treated as) immutable, and
      // hence we don't need to copy them.
      return new SparseTensor(newDimensions, getDimensionSizes(), keyNums, values);
    }

    int[] sortedDims = Arrays.copyOf(newDimensions, newDimensions.length);
    Arrays.sort(sortedDims);

    // Figure out the mapping from the new, sorted dimension indices to
    // the current indices of the outcome table.
    Map<Integer, Integer> currentDimInds = Maps.newHashMap();
    for (int i = 0; i < newDimensions.length; i++) {
      currentDimInds.put(newDimensions[i], i);
    }

    int[] sortedSizes = new int[newDimensions.length];
    int[] sortedIndexOffsets = new int[newDimensions.length];
    int[] newOrder = new int[sortedDims.length];
    int[] dimensionSizes = getDimensionSizes();
    int curIndexOffset = 1;
    for (int i = sortedDims.length - 1; i >= 0; i--) {
      newOrder[currentDimInds.get(sortedDims[i])] = i;
      sortedSizes[i] = dimensionSizes[currentDimInds.get(sortedDims[i])];
      sortedIndexOffsets[i] = curIndexOffset;
      curIndexOffset *= sortedSizes[i];
    }

    double[] resultValues = Arrays.copyOf(values, values.length);
    // Map each key of this into a key of the relabeled tensor.
    long[] resultKeyInts = new long[values.length];
    for (int i = 0; i < keyNums.length; i++) {
      long curKey = keyNums[i];
      long newKey = 0;
      for (int j = 0; j < numDimensions(); j++) {
        long dimensionValue = curKey / indexOffsets[j];
        curKey -= dimensionValue * indexOffsets[j];
        newKey += dimensionValue * sortedIndexOffsets[newOrder[j]];
      }
      resultKeyInts[i] = newKey;
    }

    sortOutcomeTable(resultKeyInts, resultValues, 0, values.length);
    return new SparseTensor(sortedDims, sortedSizes, resultKeyInts, resultValues);
  }

  /**
   * Quicksorts the section of {@code keyNums} from {@code startInd} (inclusive)
   * to {@code endInd} (not inclusive), simultaneously swapping the
   * corresponding entries of {@code values}.
   */
  private void sortOutcomeTable(long[] keyNums, double[] values,
      int startInd, int endInd) {
    // Base case.
    if (startInd == endInd) {
      return;
    }

    // Choose pivot.
    int pivotInd = (int) (Math.random() * (endInd - startInd)) + startInd;

    // Perform swaps to partition array around the pivot.
    swap(keyNums, values, startInd, pivotInd);
    pivotInd = startInd;

    for (int i = startInd + 1; i < endInd; i++) {
      if (keyNums[i] < keyNums[pivotInd]) {
        swap(keyNums, values, pivotInd, pivotInd + 1);
        if (i != pivotInd + 1) {
          swap(keyNums, values, pivotInd, i);
        }
        pivotInd++;
      }
    }

    // Recursively sort the subcomponents of the arrays.
    sortOutcomeTable(keyNums, values, startInd, pivotInd);
    sortOutcomeTable(keyNums, values, pivotInd + 1, endInd);
  }

  /**
   * Swaps the keyNums and values at {@code i} with those at {@code j}.
   * 
   * @param keyNums
   * @param values
   * @param i
   * @param j
   */
  private void swap(long[] keyNums, double[] values, int i, int j) {
    long keySwap = keyNums[i];
    keyNums[i] = keyNums[j];
    keyNums[j] = keySwap;

    double swapValue = values[i];
    values[i] = values[j];
    values[j] = swapValue;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<");
    for (int i = 0; i < values.length; i++) {
      sb.append(Arrays.toString(keyNumToDimKey(keyNums[i])));
      sb.append(" : ");
      sb.append(values[i]);
      if (i != values.length - 1) {
        sb.append(", ");
      }
    }
    sb.append(">");
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SparseTensor)) {
      return false;
    }
    SparseTensor other = (SparseTensor) o;

    return Arrays.equals(getDimensionNumbers(), other.getDimensionNumbers()) &&
        Arrays.equals(getDimensionSizes(), other.getDimensionSizes()) &&
        Arrays.equals(keyNums, other.keyNums) &&
        Arrays.equals(values, other.values);
  }

  // ///////////////////////////////////////////////////////////////////////////////
  // Static Methods
  // ///////////////////////////////////////////////////////////////////////////////

  /**
   * Gets a {@code SparseTensor} representing a scalar value. The returned table
   * has no dimensions. Multiplying another table by the returned value is
   * equivalent to multiplying each entry of the table by {@code value}.
   * 
   * @param value
   * @return
   */
  public static SparseTensor getScalarConstant(double value) {
    return new SparseTensor(new int[0], new int[0], new long[] { 0 }, new double[] { value });
  }

  /**
   * Gets an empty {@code SparseTensor} over {@code dimensionNumbers}. The value
   * of every key in the returned table is 0. Note that {@code dimensionNumbers}
   * must be sorted in ascending order.
   * 
   * @param dimensionNumbers
   * @return
   */
  public static SparseTensor empty(int[] dimensionNumbers, int[] dimensionSizes) {
    return new SparseTensor(dimensionNumbers, dimensionSizes,
        new long[0], new double[0]);
  }

  /**
   * Same as {@link #empty}, except with a {@code List} of dimensions instead of
   * an array.
   * 
   * @param dimensionNumbers
   * @return
   */
  public static SparseTensor empty(List<Integer> dimensionNumbers, List<Integer> dimensionSizes) {
    return empty(Ints.toArray(dimensionNumbers), Ints.toArray(dimensionSizes));
  }

  /**
   * Creates a one-dimensional tensor (a vector) with the given dimension number
   * and size. {@code values} is a dense representation of the vector, i.e., its
   * {@code i}th value will be the {@code i}th value of the returned tensor.
   * 
   * @param dimensionNumber
   * @param dimensionSize
   * @param values
   * @return
   */
  public static SparseTensor vector(int dimensionNumber, int dimensionSize, double[] values) {
    long[] keyNums = new long[dimensionSize];
    double[] outcomeValues = new double[dimensionSize];

    int numEntries = 0;
    for (int i = 0; i < dimensionSize; i++) {
      if (values[i] != 0.0) {
        keyNums[numEntries] = i;
        outcomeValues[numEntries] = values[i];
        numEntries++;
      }
    }

    return resizeIntoTable(new int[] { dimensionNumber }, new int[] { dimensionSize },
        keyNums, outcomeValues, numEntries);
  }

  // ///////////////////////////////////////////////////////////////////////////////
  // Private Methods
  // ///////////////////////////////////////////////////////////////////////////////

  /**
   * Takes the data structures for a {@code SparseTensor}, but possibly with the
   * wrong number of filled-in entries, resizes them and constructs a
   * {@code SparseTensor} with them.
   * 
   * @param keyNums
   * @param values
   * @param size
   * @return
   */
  private static SparseTensor resizeIntoTable(int[] dimensions, int[] dimensionSizes,
      long[] keyNums, double[] values, int size) {
    if (values.length == size) {
      return new SparseTensor(dimensions, dimensionSizes, keyNums, values);
    } else {
      // Resize the result array to fit the actual number of result keyNums.
      long[] shrunkResultKeyInts = Arrays.copyOf(keyNums, size);
      double[] shrunkResultValues = Arrays.copyOf(values, size);
      return new SparseTensor(dimensions, dimensionSizes, shrunkResultKeyInts, shrunkResultValues);
    }
  }
}