package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
public class SparseTensor {

  private int[] dimensionNums;
  private int[][] outcomes;
  private double[] values;

  public SparseTensor(int[] dimensionNums, int[][] outcomes, double[] values) {
    this.dimensionNums = Preconditions.checkNotNull(dimensionNums);
    this.outcomes = Preconditions.checkNotNull(outcomes);
    this.values = Preconditions.checkNotNull(values);

    Preconditions.checkArgument(Ordering.natural().isOrdered(Ints.asList(dimensionNums)));
    // Each element of outcomes must be the same length as values.
    for (int i = 0; i < outcomes.length; i++) {
      Preconditions.checkArgument(outcomes[i].length == values.length);
    }
    Preconditions.checkArgument(dimensionNums.length == outcomes.length);
  }

  /**
   * Gets the dimension numbers spanned by this tensor.
   * 
   * @return
   */
  public int[] getDimensionNumbers() {
    return Arrays.copyOf(dimensionNums, dimensionNums.length);
  }

  /**
   * Gets the number of keys stored in {@code this}.
   * 
   * @return
   */
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
    return findIndex(key) != -1;
  }

  /**
   * Get the value associated with a variable assignment. Returns {@code 0.0} if
   * no value is associated with {@code key}.
   */
  public double get(int[] key) {
    int index = findIndex(key);
    if (index != -1) {
      return values[index];
    }
    return 0.0;
  }

  /**
   * Returns an iterator over all assignments (keys) in this table.
   */
  public Iterator<int[]> keyIterator() {
    return new KeyIterator(outcomes, values.length);
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
  public SparseTensor elementwiseProduct(SparseTensor other) {
    Set<Integer> myDims = Sets.newHashSet(Ints.asList(dimensionNums));
    Preconditions.checkArgument(myDims.containsAll(Ints.asList(other.dimensionNums)));
    
    // Permute the dimensions of this so that the dimension of other are
    // left-aligned,
    // multiply, then reverse the permutation.
    BiMap<Integer, Integer> permutation = HashBiMap.create();
    int numDimsDifferent = 0;
    int otherInd = 0;
    for (int i = 0; i < dimensionNums.length; i++) {
      if (otherInd < other.dimensionNums.length && other.dimensionNums[otherInd] > dimensionNums[i]) {
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
   * the left side of their respective {@code outcomes} arrays.
   * 
   * @param big
   * @param small
   * @return
   */
  protected static final SparseTensor elementwiseMultiplyLeftAligned(SparseTensor big, SparseTensor small) {
    // The result tensor is no larger than the larger (superset of dimensions)
    // tensor.
    int[][] resultOutcomes = new int[big.outcomes.length][big.values.length];
    double[] resultValues = new double[big.values.length];
    // How many result values have been filled so far.
    int resultInd = 0;

    // Current positions in each tensor's key/value array.
    int myInd = 0;
    int otherInd = 0;

    for (myInd = 0; myInd < big.values.length; myInd = advance(myInd, big.outcomes, otherInd, small.outcomes)) {
      // Advance otherInd until other's outcome is >= our outcome.
      while (otherInd < small.values.length &&
          compareOutcomes(big.outcomes, myInd, small.outcomes, otherInd) > 0) {
        otherInd++;
      }

      if (otherInd < small.values.length &&
          compareOutcomes(big.outcomes, myInd, small.outcomes, otherInd) == 0) {
        copyOutcome(big.outcomes, myInd, resultOutcomes, resultInd);
        resultValues[resultInd] = big.values[myInd] * small.values[otherInd];
        resultInd++;
      }
    }
    return resizeIntoTable(big.dimensionNums, resultOutcomes, resultValues, resultInd);
  }

  private static final int advance(int myInd, int[][] myOutcomes, int otherInd, int[][] otherOutcomes) {
    // This algorithm advances myInd into a block of outcomes where the first
    // coordinate
    // of this tensor's outcomes matches the first coordinate of the other
    // tensor's outcome.
    if (otherOutcomes.length == 0) {
      return myInd + 1;
    } else if (otherInd == otherOutcomes[0].length) {
      return myOutcomes[0].length;
    } else if (myOutcomes[0][myInd] == otherOutcomes[0][otherInd]) {
      // We're within a block of outcomes of other that start with the same
      // coordinate as myOutcomes.
      return myInd + 1;
    } else {
      // Find a block of outcomes with the correct starting coordinate.
      return binarySearch(myOutcomes[0], otherOutcomes[0][otherInd], myInd, myOutcomes[0].length);
    }
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
  private static final int binarySearch(int[] array, int value, int startInd, int endInd) {
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
   * 
   * from startIndex (inclusive) to endIndex (not inclusive).
   * 
   * @param targetOutcomes
   * @param targetOutcomeInd
   * @param startIndex
   * @param endIndex
   * @param findStart if {@code true}, finds the beginning of the block.
   * Otherwise finds the end.
   * @return
   */
  /*
   * private int getKeyBlockIndex(int[][] targetOutcomes, int targetOutcomeInd,
   * int startIndex, int endIndex, boolean findStart) { if (startIndex ==
   * endIndex) { // outcome was not found return -1; }
   * 
   * // Binary search this.outcomes to find the start of the block. int curInd =
   * (startIndex + endIndex) / 2; int cmpResult = compareOutcomes(outcomes,
   * curInd, targetOutcomes, targetOutcomeInd); if (cmpResult > 0) { //
   * outcomes[curInd] comes after targetOutcomes[targetOutcomeInd]. return
   * getKeyBlockStartIndex(targetOutcomes, targetOutcomeInd, startIndex,
   * curInd); } else if (cmpResult < 0) { // outcomes[curInd] comes before
   * targetOutcomes[targetOutcomeInd]. return
   * getKeyBlockStartIndex(targetOutcomes, targetOutcomeInd, curInd + 1,
   * endIndex); } return curInd; }
   */

  /**
   * Performs elementwise addition of {@code this} and {@code other} and returns
   * a new {@code SparseTensor} containing the result. The value of key {code k}
   * in the returned table is {@code this.get(k) + other.get(k)}. Requires
   * {@code other} and {@code this} to contain the same dimensions.
   * 
   * @param other
   * @return
   */
  public SparseTensor elementwiseAddition(SparseTensor other) {
    return doElementwise(other, true);
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
  public SparseTensor elementwiseMaximum(SparseTensor other) {
    return doElementwise(other, false);
  }

  /**
   * Helper method for performing {@link #elementwiseAddition} and
   * {@link #elementwiseMaximum}. If {@code useSum == true} this adds the values
   * of equivalent outcomes; otherwise, it takes the maximum of the two
   * outcomes.
   */
  private SparseTensor doElementwise(SparseTensor other, boolean useSum) {
    // TODO(jayantk): This method could be generalized by taking a Function
    // instead of a boolean flag. However, it's unclear how this change
    // affects performance.
    Preconditions.checkArgument(Arrays.equals(dimensionNums, other.dimensionNums));
    int[][] resultOutcomes = new int[dimensionNums.length][values.length + other.values.length];
    double[] resultValues = new double[values.length + other.values.length];

    int resultInd = 0;
    int myInd = 0;
    int otherInd = 0;
    while (myInd < values.length && otherInd < other.values.length) {
      boolean equal = true;
      for (int j = 0; j < outcomes.length; j++) {
        if (outcomes[j][myInd] < other.outcomes[j][otherInd]) {
          copyOutcome(outcomes, myInd, resultOutcomes, resultInd);
          resultValues[resultInd] = values[myInd];
          resultInd++;
          myInd++;
          equal = false;
          break;
        } else if (outcomes[j][myInd] > other.outcomes[j][otherInd]) {
          copyOutcome(other.outcomes, otherInd, resultOutcomes, resultInd);
          resultValues[resultInd] = values[otherInd];
          otherInd++;
          resultInd++;
          equal = false;
          break;
        }
      }

      if (equal) {
        copyOutcome(outcomes, myInd, resultOutcomes, resultInd);
        if (useSum) {
          resultValues[resultInd] = values[myInd] + other.values[otherInd];
        } else {
          resultValues[resultInd] = Math.max(values[myInd], other.values[otherInd]);
        }
        resultInd++;
        myInd++;
        otherInd++;
      }
    }

    // One of the two lists might not be done yet. Finish it off.
    for (; myInd < values.length; myInd++) {
      copyOutcome(outcomes, myInd, resultOutcomes, resultInd);
      resultValues[resultInd] = values[myInd];
      resultInd++;
    }

    for (; otherInd < other.values.length; otherInd++) {
      copyOutcome(other.outcomes, otherInd, resultOutcomes, resultInd);
      resultValues[resultInd] = other.values[otherInd];
      resultInd++;
    }

    return resizeIntoTable(dimensionNums, resultOutcomes, resultValues, resultInd);
  }

  /**
   * Returns the elementwise multiplicative inverse of {@code this}. For all
   * keys {@code k} in {@code this}, {@code inverse.get(k) * this.get(k) == 1}.
   * For all keys not in {@code this}, {@code inverse.get(k) == 0}.
   * 
   * @return
   */
  public SparseTensor elementwiseInverse() {
    double[] newValues = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      newValues[i] = 1.0 / values[i];
    }
    // We don't have to copy outcomes because this class is immutable, and it
    // treats both outcomes and values as immutable.
    return new SparseTensor(dimensionNums, outcomes, newValues);
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
  public SparseTensor sumOutDimensions(Set<Integer> dimensionsToEliminate) {
    return reduceDimensions(dimensionsToEliminate, true);
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
  public SparseTensor maxOutDimensions(Set<Integer> dimensionsToEliminate) {
    return reduceDimensions(dimensionsToEliminate, false);
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
    // end of the outcomes array.
    int[] newLabels = new int[dimensionNums.length];
    int[] newDimensions = new int[dimensionNums.length];
    int numEliminated = 0;
    for (int i = 0; i < dimensionNums.length; i++) {
      if (dimensionsToEliminate.contains(dimensionNums[i])) {
        // Dimension labels must be unique, hence numEliminated.
        newLabels[i] = Integer.MAX_VALUE - numEliminated;
        numEliminated++;
      } else {
        newLabels[i] = dimensionNums[i];
        newDimensions[i - numEliminated] = dimensionNums[i];
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
    int[][] resultOutcomes = new int[resultNumDimensions][relabeled.values.length];
    double[] resultValues = new double[relabeled.values.length];
    int resultInd = 0;
    for (int i = 0; i < relabeled.values.length; i++) {
      boolean equal = (i != 0);
      for (int j = 0; j < resultNumDimensions; j++) {
        if (i == 0 || relabeled.outcomes[j][i] != relabeled.outcomes[j][i - 1]) {
          equal = false;
          break;
        }
      }

      if (equal) {
        if (useSum) {
          resultValues[resultInd - 1] += relabeled.values[i];
        } else {
          resultValues[resultInd - 1] = Math.max(resultValues[resultInd - 1],
              relabeled.values[i]);
        }
      } else {
        copyOutcome(relabeled.outcomes, i, resultOutcomes, resultInd);
        resultValues[resultInd] = relabeled.values[i];
        resultInd++;
      }
    }

    return resizeIntoTable(Arrays.copyOf(newDimensions, resultNumDimensions),
        resultOutcomes, resultValues, resultInd);
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
  public SparseTensor relabelDimensions(int[] newDimensions) {
    Preconditions.checkArgument(newDimensions.length == dimensionNums.length);
    if (Ordering.natural().isOrdered(Ints.asList(newDimensions))) {
      // If the new dimension labels are in sorted order, then we don't have to
      // resort the outcome and value arrays. This is a big efficiency win if it
      // happens. Note that outcomes and values are (treated as) immutable, and
      // hence
      // we don't need to copy them.
      return new SparseTensor(newDimensions, outcomes, values);
    }

    int[] sortedDims = Arrays.copyOf(newDimensions, newDimensions.length);
    Arrays.sort(sortedDims);

    // Figure out the mapping from the new, sorted dimension indices to
    // the current indicies of the outcome table.
    Map<Integer, Integer> currentDimInds = Maps.newHashMap();
    for (int i = 0; i < newDimensions.length; i++) {
      currentDimInds.put(newDimensions[i], i);
    }
    int[] newOrder = new int[sortedDims.length];
    for (int i = 0; i < sortedDims.length; i++) {
      newOrder[i] = currentDimInds.get(sortedDims[i]);
    }

    int[][] resultOutcomes = new int[dimensionNums.length][];
    double[] resultValues = Arrays.copyOf(values, values.length);
    for (int i = 0; i < resultOutcomes.length; i++) {
      resultOutcomes[i] = Arrays.copyOf(outcomes[newOrder[i]], values.length);
    }

    sortOutcomeTable(resultOutcomes, resultValues, 0, values.length);
    return new SparseTensor(sortedDims, resultOutcomes, resultValues);
  }

  /**
   * Quicksorts the section of {@code outcomes} from {@code startInd}
   * (inclusive) to {@code endInd} (not inclusive). The sort of outcomes
   * proceeds by dimension, where the first dimension is the most significant.
   */
  private void sortOutcomeTable(int[][] outcomes, double[] values,
      int startInd, int endInd) {
    // Base case.
    if (startInd == endInd) {
      return;
    }

    // Choose pivot.
    int pivotInd = (int) (Math.random() * (endInd - startInd)) + startInd;

    // Perform swaps to partition array around the pivot.
    swap(outcomes, values, startInd, pivotInd);
    pivotInd = startInd;

    for (int i = startInd + 1; i < endInd; i++) {
      for (int j = 0; j < outcomes.length; j++) {
        if (outcomes[j][i] < outcomes[j][pivotInd]) {
          swap(outcomes, values, pivotInd, pivotInd + 1);
          if (i != pivotInd + 1) {
            swap(outcomes, values, pivotInd, i);
          }
          pivotInd++;
          break;
        } else if (outcomes[j][i] > outcomes[j][pivotInd]) {
          break;
        }
      }
    }

    // Recursively sort the subcomponents of the array.
    sortOutcomeTable(outcomes, values, startInd, pivotInd);
    sortOutcomeTable(outcomes, values, pivotInd + 1, endInd);
  }

  /**
   * Swaps the outcomes and values at {@code i} with those at {@code j}.
   * 
   * @param outcomes
   * @param values
   * @param i
   * @param j
   */
  private void swap(int[][] outcomes, double[] values, int i, int j) {
    for (int k = 0; k < outcomes.length; k++) {
      int swap = outcomes[k][i];
      outcomes[k][i] = outcomes[k][j];
      outcomes[k][j] = swap;
    }
    double swapValue = values[i];
    values[i] = values[j];
    values[j] = swapValue;
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
  public SparseTensor relabelDimensions(Map<Integer, Integer> relabeling) {
    int[] newDimensions = new int[dimensionNums.length];
    for (int i = 0; i < dimensionNums.length; i++) {
      newDimensions[i] = relabeling.get(dimensionNums[i]);
    }
    return relabelDimensions(newDimensions);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      sb.append("[");
      for (int j = 0; j < outcomes.length; j++) {
        if (j > 0) {
          sb.append(", ");
        }
        sb.append(outcomes[j][i]);
      }
      sb.append("] : ");
      sb.append(values[i]);
      sb.append("\n");
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SparseTensor)) {
      return false;
    }
    SparseTensor other = (SparseTensor) o;

    if (Arrays.equals(dimensionNums, other.dimensionNums) &&
        values.length == other.values.length) {
      for (int i = 0; i < values.length; i++) {
        for (int j = 0; j < outcomes.length; j++) {
          if (outcomes[j][i] != other.outcomes[j][i]) {
            return false;
          }
        }
        if (values[i] != other.values[i]) {
          return false;
        }
      }
      return true;
    }
    return false;
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
    return new SparseTensor(new int[0], new int[0][0], new double[] { value });
  }

  /**
   * Gets an empty {@code SparseTensor} over {@code dimensionNumbers}. The value
   * of every key in the returned table is 0. Note that {@code dimensionNumbers}
   * must be sorted in ascending order.
   * 
   * @param dimensionNumbers
   * @return
   */
  public static SparseTensor empty(int[] dimensionNumbers) {
    return new SparseTensor(dimensionNumbers,
        new int[dimensionNumbers.length][0], new double[0]);
  }

  /**
   * Same as {@link #empty}, except with a {@code List} of dimensions instead of
   * an array.
   * 
   * @param dimensionNumbers
   * @return
   */
  public static SparseTensor empty(List<Integer> dimensionNumbers) {
    return empty(Ints.toArray(dimensionNumbers));
  }

  // ///////////////////////////////////////////////////////////////////////////////
  // Private Methods
  // ///////////////////////////////////////////////////////////////////////////////

  /**
   * If {@code key} is in this map, returns its index in {@code this.outcomes}.
   * Otherwise, returns -1.
   * 
   * @param key
   * @return
   */
  private int findIndex(int[] key) {
    Preconditions.checkArgument(key.length == outcomes.length);
    // TODO(jayantk): This algorithm could use binary search to be more
    // efficient.
    for (int i = 0; i < values.length; i++) {
      boolean foundKey = true;
      for (int j = 0; j < outcomes.length; j++) {
        if (outcomes[j][i] != key[j]) {
          foundKey = false;
          break;
        }
      }
      if (foundKey) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Takes the data structures for a {@code SparseTensor}, but possibly with the
   * wrong number of filled-in entries, resizes them and constructs a
   * {@code SparseTensor} with them.
   * 
   * @param outcomes
   * @param values
   * @param size
   * @return
   */
  private static SparseTensor resizeIntoTable(int[] dimensions, int[][] outcomes,
      double[] values, int size) {
    if (values.length == size) {
      return new SparseTensor(dimensions, outcomes, values);
    } else {
      // Resize the result array to fit the actual number of result outcomes.
      int[][] shrunkResultOutcomes = new int[outcomes.length][];
      for (int j = 0; j < outcomes.length; j++) {
        shrunkResultOutcomes[j] = Arrays.copyOf(outcomes[j], size);
      }
      double[] shrunkResultValues = Arrays.copyOf(values, size);
      return new SparseTensor(dimensions, shrunkResultOutcomes, shrunkResultValues);
    }
  }

  /**
   * Copies outcome {@code fromInd} from {@code copyFrom} into {@code toInd} of
   * {@code copyTo}. If {@code copyFrom.length > copyTo.length}, the first
   * {@code copyTo.length} coordinates are copied.
   * 
   * @param copyFrom
   * @param fromInd
   * @param copyTo
   * @param toInd
   */
  private static final void copyOutcome(int[][] copyFrom, int fromInd,
      int[][] copyTo, int toInd) {
    for (int j = 0; j < copyTo.length; j++) {
      copyTo[j][toInd] = copyFrom[j][fromInd];
    }
  }

  /**
   * Returns 1 if the firstInd'th outcome of firstOutcomes is greater (comes
   * later in the list) than the secondInd'th outcome of secondOutcomes. Returns
   * -1 if the opposite is true, or 0 if they are equal.
   * 
   * {@code alignment} determines which dimensions of {@code firstOutcomes} are
   * compared to each dimension {@code secondOutcomes}.
   * 
   * @param firstOutcomes
   * @param firstInd
   * @param secondOutcomes
   * @param secondInd
   */
  private static final int compareOutcomes(int[][] firstOutcomes, int firstInd,
      int[][] secondOutcomes, int secondInd) {
    for (int j = 0; j < secondOutcomes.length; j++) {
      if (firstOutcomes[j][firstInd] > secondOutcomes[j][secondInd]) {
        return 1;
      } else if (firstOutcomes[j][firstInd] < secondOutcomes[j][secondInd]) {
        return -1;
      }
    }
    return 0;
  }

  /**
   * Helper class for iterating over keys of this tensor.
   */
  private class KeyIterator implements Iterator<int[]> {

    private int curIndex;
    private final int[][] outcomes;
    private final int numOutcomes;

    public KeyIterator(int[][] outcomes, int numOutcomes) {
      this.outcomes = outcomes;
      this.numOutcomes = numOutcomes;
      this.curIndex = 0;
    }

    @Override
    public boolean hasNext() {
      return curIndex < numOutcomes;
    }

    @Override
    public int[] next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      int[] outcome = new int[outcomes.length];
      for (int i = 0; i < outcomes.length; i++) {
        outcome[i] = outcomes[i][curIndex];
      }
      curIndex++;
      return outcome;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}