package com.jayantkrish.jklol.tensor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.tensor.TensorProtos.SparseTensorProto;
import com.jayantkrish.jklol.tensor.TensorProtos.TensorProto;
import com.jayantkrish.jklol.util.HeapUtils;

/**
 * A SparseTensor sparsely stores a mapping from int[] to double. This class
 * represents a sparse tensor, where all values are presumed to be 0 unless
 * otherwise specified.
 * 
 * SparseTensors are immutable.
 */
public class SparseTensor extends AbstractTensor implements Serializable{

  protected final long[] keyNums;
  protected final double[] values;

  public SparseTensor(int[] dimensionNums, int[] dimensionSizes, long[] keyNums, double[] values) {
    super(dimensionNums, dimensionSizes);
    Preconditions.checkArgument(Ordering.natural().isOrdered(Ints.asList(dimensionNums)));
    Preconditions.checkArgument(keyNums.length == values.length);
    Preconditions.checkArgument(keyNums.length == 0 || keyNums[0] >= 0,
        "Tried creating a tensor with negative keyNums.");

    this.keyNums = Preconditions.checkNotNull(keyNums);
    this.values = Preconditions.checkNotNull(values);
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
   * Returns {@code true} if {@code key} has a value in this.
   * 
   * @param key
   * @return
   */
  public boolean containsKey(int[] key) {
    return keyNumToIndex(dimKeyToKeyNum(key)) != -1;
  }

  @Override
  public double getByIndex(int index) {
    if (index == -1) {
      return 0.0;
    }
    return values[index];
  }

  @Override
  public double getLogByIndex(int index) {
    return Math.log(getByIndex(index));
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

  @Override
  public int getNearestIndex(long keyNum) {
    int index = Arrays.binarySearch(keyNums, keyNum);
    if (index < 0) {
      index = (-1 * index) - 1;
    }
    return index;
  }

  @Override
  public double[] getValues() {
    return values;
  }

  /**
   * Returns an iterator over all assignments (keys) in this table.
   */
  @Override
  public Iterator<KeyValue> keyValueIterator() {
    return new SparseKeyValueIterator(keyNums, values, 0, keyNums.length, this);
  }

  @Override
  public Iterator<KeyValue> keyValuePrefixIterator(int[] keyPrefix) {
    if (keyPrefix.length == 0) {
      return keyValueIterator();
    }

    long startKeyNum = dimKeyPrefixToKeyNum(keyPrefix);
    long endKeyNum = startKeyNum + indexOffsets[keyPrefix.length - 1];
    return new SparseKeyValueIterator(keyNums, values, getNearestIndex(startKeyNum),
        getNearestIndex(endKeyNum), this);
  }

  @Override
  public double getL2Norm() {
    double sumSquared = 0.0;
    for (int i = 0; i < size(); i++) {
      sumSquared += values[i] * values[i];
    }
    return Math.sqrt(sumSquared);
  }

  @Override
  public double getTrace() {
    double sum = 0.0;
    for (int i = 0; i < size(); i++) {
      sum += values[i];
    }
    return sum;
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

      int startIndex = getNearestIndex(minKeyInt);
      int endIndex = getNearestIndex(maxKeyInt);

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

    // There are two possible multiplication operations: an extremely fast
    // one for the case when the dimensions of other line up against the
    // leftmost dimensions of this, and another for all other cases.
    // The left-aligned case can be made more efficient than the other
    // case. Check which case applies, then use the faster algorithm.
    int[] otherDimensions = other.getDimensionNumbers();
    for (int i = 0; i < otherDimensions.length; i++) {
      if (otherDimensions[i] != dimensionNums[i]) {
        // Not left aligned.
        return elementwiseMultiplyNaive(this, other);
      }
    }
    // Left aligned.
    return elementwiseMultiplyLeftAligned(this, other);
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
    int bigInd = 0;
    int smallInd = advanceToNonzero(-1, small);

    long smallIndexMultiplier = 1;
    for (int i = big.numDimensions() - 1; i >= small.numDimensions(); i--) {
      smallIndexMultiplier *= ((long) big.getDimensionSizes()[i]);
    }

    // Variables for the binary search
    int startInd, endInd, cmpInd;
    long targetKeyNum;

    // Caches to avoid recomputing method values.
    int bigSize = big.size();
    int smallSize = small.size();
    long bigKeyNum, smallKeyNum, bigKeyNumDividedByMultiplier;
    outerloop: for (bigInd = 0; bigInd < bigSize && smallInd < smallSize;) {

      // Advance smallInd until other's outcome is >= our outcome.
      bigKeyNum = big.indexToKeyNum(bigInd);
      bigKeyNumDividedByMultiplier = bigKeyNum / smallIndexMultiplier;
      while (bigKeyNumDividedByMultiplier > small.indexToKeyNum(smallInd) ||
          small.getByIndex(smallInd) == 0) {
        smallInd++;
        if (smallInd >= smallSize) {
          break outerloop;
        }
      }
      smallKeyNum = small.indexToKeyNum(smallInd);

      if (bigKeyNumDividedByMultiplier == smallKeyNum) {
        resultKeyInts[resultInd] = bigKeyNum;
        resultValues[resultInd] = big.values[bigInd] * small.getByIndex(smallInd);
        resultInd++;
        bigInd++;
      } else {
        // Advance bigInd by a large amount.

        // Find a block of outcomes with the correct starting coordinates.
        // This performs a binary search between the starting value of bigInd
        // and the end of the array.
        startInd = bigInd + 1;
        endInd = big.keyNums.length;
        targetKeyNum = smallKeyNum * smallIndexMultiplier;
        while (startInd != endInd) {
          cmpInd = (startInd + endInd) / 2;
          if (targetKeyNum > big.keyNums[cmpInd]) {
            startInd = cmpInd + 1;
          } else {
            endInd = cmpInd;
          }
        }
        bigInd = startInd;
      }
    }
    return resizeIntoTable(big.getDimensionNumbers(), big.getDimensionSizes(),
        resultKeyInts, resultValues, resultInd);
  }

  private static final int advanceToNonzero(int startInd, Tensor other) {
    int nextInd = startInd + 1;
    while (nextInd < other.size() && other.getByIndex(nextInd) == 0) {
      nextInd++;
    }
    return nextInd;
  }

  /**
   * Naive elementwise multiplication: for each element of {@code big}, find a
   * corresponding element in {@code small} and multiply them.
   * 
   * @param big
   * @param small
   * @return
   */
  protected static final SparseTensor elementwiseMultiplyNaive(SparseTensor big, Tensor small) {
    // Compute a mapping from keyNums of big to keyNums of small.
    int[] bigDimensions = big.getDimensionNumbers();
    int[] smallDimensions = small.getDimensionNumbers();
    int[] bigDimensionSizes = big.getDimensionSizes();
    int smallDimensionInd = smallDimensions.length - 1;
    int bigDimensionInd = bigDimensions.length - 1;
    // divisors, modulos and multipliers store the mapping from bigKeyNum to
    // smallKeyNum.
    // They contain a series of division, modulo and multiplication operations
    // whose results are added to get a smallKeyNum.
    long[] divisors = new long[smallDimensions.length];
    long[] modulos = new long[smallDimensions.length];
    long[] multipliers = new long[smallDimensions.length];
    int divisorInd = 0;
    long divisor = 1;
    long multiplier = 1;
    long modulo = 1;
    while (bigDimensionInd >= 0 && smallDimensionInd >= 0) {
      while (bigDimensions[bigDimensionInd] != smallDimensions[smallDimensionInd]) {
        divisor *= bigDimensionSizes[bigDimensionInd];
        bigDimensionInd--;
      }
      divisors[divisorInd] = divisor;

      modulo = 1;
      while (bigDimensionInd >= 0 && smallDimensionInd >= 0 &&
          bigDimensions[bigDimensionInd] == smallDimensions[smallDimensionInd]) {
        divisor *= bigDimensionSizes[bigDimensionInd];
        modulo *= bigDimensionSizes[bigDimensionInd];
        bigDimensionInd--;
        smallDimensionInd--;
      }
      modulos[divisorInd] = modulo;
      multipliers[divisorInd] = multiplier;
      divisorInd++;
      multiplier *= modulo;
    }

    // The result tensor is no larger than the larger (superset of dimensions)
    // tensor.
    long[] resultKeyInts = new long[big.size()];
    double[] resultValues = new double[big.size()];
    // How many result values have been filled so far.
    int resultInd = 0;

    long bigKeyNum, smallKeyNum;
    double value;
    int numElements = big.size();
    for (int bigInd = 0; bigInd < numElements; bigInd++) {
      bigKeyNum = big.indexToKeyNum(bigInd);
      // map bigKeyNum to a smallKeyNum
      smallKeyNum = 0;
      for (int i = 0; i < divisorInd; i++) {
        smallKeyNum += ((bigKeyNum / divisors[i]) % modulos[i]) * multipliers[i];
      }
      value = small.get(smallKeyNum);

      if (value != 0.0) {
        resultKeyInts[resultInd] = bigKeyNum;
        resultValues[resultInd] = value * big.getByIndex(bigInd);
        resultInd++;
      }
    }

    return resizeIntoTable(big.getDimensionNumbers(), big.getDimensionSizes(), resultKeyInts,
        resultValues, resultInd);
  }

  @Override
  public SparseTensor outerProduct(Tensor other) {
    int[] dimensionNums = getDimensionNumbers();
    int[] otherDimensionNums = other.getDimensionNumbers();
    if (otherDimensionNums.length == 0) {
      // The two products coincide in this case.
      return elementwiseProduct(other);
    }

    Preconditions.checkArgument(dimensionNums[dimensionNums.length - 1]
        < otherDimensionNums[otherDimensionNums.length - 1]);

    long multiplier = other.getMaxKeyNum();
    int mySize = size();
    int otherSize = other.size();
    long[] resultKeyNums = new long[mySize * otherSize];
    double[] resultValues = new double[mySize * otherSize];
    int resultInd = 0;
    for (int i = 0; i < mySize; i++) {
      long keyNumOffset = keyNums[i] * multiplier;
      double myValue = values[i];
      for (int j = 0; j < otherSize; j++) {
        double otherValue = other.getByIndex(j);
        if (otherValue != 0.0) {
          resultKeyNums[resultInd] = keyNumOffset + other.indexToKeyNum(j);
          resultValues[resultInd] = myValue * otherValue;
          resultInd++;
        }
      }
    }

    int[] dimensionSizes = getDimensionSizes();
    int[] otherDimensionSizes = other.getDimensionSizes();
    int[] resultDims = new int[dimensionNums.length + otherDimensionNums.length];
    int[] resultSizes = new int[dimensionNums.length + otherDimensionNums.length];
    for (int i = 0; i < dimensionNums.length; i++) {
      resultDims[i] = dimensionNums[i];
      resultSizes[i] = dimensionSizes[i];
    }

    for (int i = 0; i < otherDimensionNums.length; i++) {
      resultDims[i + dimensionNums.length] = otherDimensionNums[i];
      resultSizes[i + dimensionNums.length] = otherDimensionSizes[i];
    }

    return resizeIntoTable(resultDims, resultSizes, resultKeyNums, resultValues, resultInd);
  }

  @Override
  public SparseTensor elementwiseAddition(Tensor otherTensor) {
    return doElementwise(otherTensor, true);
  }
  
  @Override
  public Tensor elementwiseAddition(double value) {
    // This kind of addition is going to destroy sparsity, 
    // so may as well use a dense tensor.
    DenseTensorBuilder result = new DenseTensorBuilder(getDimensionNumbers(),
        getDimensionSizes());
    result.increment(this);
    result.increment(value);
    return result.buildNoCopy();
  }

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
  private final SparseTensor doElementwise(Tensor other, boolean useSum) {
    // TODO(jayantk): This method could be generalized by taking a Function
    // instead of a boolean flag. However, it's unclear how this change
    // affects performance.
    Preconditions.checkArgument(Arrays.equals(getDimensionNumbers(), other.getDimensionNumbers()));
    long[] resultKeyInts = new long[size() + other.size()];
    double[] resultValues = new double[size() + other.size()];

    int resultInd = 0;
    int myInd = 0;
    int otherInd = 0;

    int mySize = size();
    int otherSize = other.size();
    long otherKeyNum = 1;
    while (myInd < mySize && otherInd < otherSize) {
      otherKeyNum = other.indexToKeyNum(otherInd);
      if (keyNums[myInd] < otherKeyNum) {
        resultKeyInts[resultInd] = keyNums[myInd];
        resultValues[resultInd] = values[myInd];
        resultInd++;
        myInd++;
      } else if (keyNums[myInd] > otherKeyNum) {
        resultKeyInts[resultInd] = otherKeyNum;
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

  @Override
  public DenseTensor elementwiseLog() {
    // Zero entries of this map to negative infinity in the returned tensor.
    DenseTensorBuilder builder = new DenseTensorBuilder(this.getDimensionNumbers(),
        this.getDimensionSizes(), Double.NEGATIVE_INFINITY);
    for (int i = 0; i < size(); i++) {
      builder.putByKeyNum(keyNums[i], Math.log(values[i]));
    }
    return builder.buildNoCopy();
  }

  @Override
  public DenseTensor elementwiseExp() {
    // Zero entries of this map to 1.0 in the returned tensor.
    DenseTensorBuilder builder = new DenseTensorBuilder(this.getDimensionNumbers(),
        this.getDimensionSizes(), 1.0);
    for (int i = 0; i < size(); i++) {
      builder.putByKeyNum(keyNums[i], Math.exp(values[i]));
    }
    return builder.buildNoCopy();
  }

  /**
   * Sparsely computes e to the power of each element in this tensor. For keys
   * which are present in this {@code SparseTensor}, this operation is identical
   * to {@link #elementwiseExp()}. Keys which are not present in this tensor are
   * also not present in the result (and therefore have value 0).
   * 
   * @return
   */
  public SparseTensor elementwiseExpSparse() {
    double[] newValues = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      newValues[i] = Math.exp(values[i]);
    }
    return new SparseTensor(getDimensionNumbers(), getDimensionSizes(), keyNums, newValues);
  }

  @Override
  public SparseTensor sumOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return reduceDimensions(Sets.newHashSet(dimensionsToEliminate), true, null);
  }

  @Override
  public SparseTensor maxOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return reduceDimensions(Sets.newHashSet(dimensionsToEliminate), false, null);
  }

  @Override
  public SparseTensor maxOutDimensions(Collection<Integer> dimensionsToEliminate,
      Backpointers backpointers) {
    return reduceDimensions(Sets.newHashSet(dimensionsToEliminate), false, backpointers);
  }

  /**
   * Eliminates {@code dimensionsToEliminate}, either by summing or maximizing.
   * 
   * @param dimensionsToEliminate
   * @param useSum
   * @return
   */
  private SparseTensor reduceDimensions(Set<Integer> dimensionsToEliminate,
      boolean useSum, Backpointers backpointers) {
    // TODO(jayantk): This method can be generalized to support a generic reduce
    // operation (as a function), but it's unclear what the effect on
    // performance will be.

    // Rotate all of the dimensions which are being eliminated to the
    // end of the keyNums array.
    int[] newLabels = new int[numDimensions()];
    int[] inversionPermutation = new int[numDimensions()];
    int[] newDimensions = new int[numDimensions()];
    int[] newDimensionSizes = new int[numDimensions()];
    int numEliminated = 0;
    int[] dimensionNums = getDimensionNumbers();
    int[] dimensionSizes = getDimensionSizes();
    for (int i = 0; i < dimensionNums.length; i++) {
      if (dimensionsToEliminate.contains(dimensionNums[i])) {
        // Dimension labels must be unique, hence numEliminated.
        newLabels[i] = Integer.MAX_VALUE - numEliminated;
        inversionPermutation[dimensionNums.length - (numEliminated + 1)] = i;
        numEliminated++;
      } else {
        newLabels[i] = dimensionNums[i];
        inversionPermutation[i - numEliminated] = i;
        newDimensions[i - numEliminated] = dimensionNums[i];
        newDimensionSizes[i - numEliminated] = dimensionSizes[i];
      }
    }
    // If none of the dimensions being eliminated are actually part of this
    // tensor, then there's no need to do any more work.
    if (numEliminated == 0) {
      if (backpointers != null) {
        backpointers.setBackpointers(keyNums, keyNums, keyNums.length, this);
      }
      return this;
    }

    SparseTensor relabeled = relabelDimensions(newLabels);
    int resultNumDimensions = dimensionNums.length - numEliminated;

    // Get a number which we can divide each key by to map it to a key in the
    // reduced dimensional tensor.
    long keyNumDenominator = (resultNumDimensions > 0) ? relabeled.indexOffsets[resultNumDimensions - 1] :
        relabeled.indexOffsets[0] * relabeled.getDimensionSizes()[0];

    long[] resultKeyInts = new long[relabeled.values.length];
    long[] backpointerKeyInts = new long[relabeled.values.length];
    double[] resultValues = new double[relabeled.values.length];

    int resultInd = 0;
    for (int i = 0; i < relabeled.values.length; i++) {
      if (i != 0 && resultInd > 0 &&
          (relabeled.keyNums[i] / keyNumDenominator) == resultKeyInts[resultInd - 1]) {
        // This key maps to the same entry as the previous key.
        if (useSum) {
          resultValues[resultInd - 1] += relabeled.values[i];
        } else {
          double resultVal = resultValues[resultInd - 1];
          double relabeledVal = relabeled.values[i];
          if (relabeledVal > resultVal) {
            resultValues[resultInd - 1] = relabeledVal;
            backpointerKeyInts[resultInd - 1] = relabeled.keyNums[i];
          }
        }
      } else {
        if (resultInd > 0 && resultValues[resultInd - 1] == 0.0) {
          // Make sure the result tensor contains no zero-valued entries.
          resultInd--;
        }

        resultKeyInts[resultInd] = relabeled.keyNums[i] / keyNumDenominator;
        backpointerKeyInts[resultInd] = relabeled.keyNums[i];
        resultValues[resultInd] = relabeled.values[i];
        resultInd++;
      }
      
      int prevIndex = resultInd - 1;
      if (!useSum && resultValues[prevIndex] < 0.0) {
        // Ensure that, if values is negative, we include missing keys in the
        // maximization.
        long prevKeyNum = relabeled.keyNums[i] - 1;
        long nextKeyNum = relabeled.keyNums[i] + 1;

        if (i > 0 && relabeled.keyNums[i - 1] != prevKeyNum 
            && prevKeyNum / keyNumDenominator == resultKeyInts[prevIndex]) {
          // prevKeyNum is not in relabeled, but has a higher value than the current key.
          resultValues[prevIndex] = 0.0;
          backpointerKeyInts[prevIndex] = prevKeyNum;
        } else if (i + 1 < relabeled.keyNums.length && relabeled.keyNums[i + 1] != nextKeyNum 
            && nextKeyNum / keyNumDenominator == resultKeyInts[prevIndex]) {
          // nextKeyNum is not in relabeled, but has a higher value than the current key.
          // Delete the current key from the tensor.
          resultValues[prevIndex] = 0.0;
          backpointerKeyInts[prevIndex] = nextKeyNum;
        }
      }
    }

    if (backpointers != null) {
      // backpointerKeyInts needs to have the inverse dimension relabeling
      // applied to it.
      long[] transformedBackpointers = transformKeyNums(backpointerKeyInts, relabeled.indexOffsets,
          this.indexOffsets, inversionPermutation);
      backpointers.setBackpointers(resultKeyInts, transformedBackpointers, resultInd, this);
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
      // re-sort the outcome and value arrays. This is a big efficiency win if
      // it
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
    long[] sortedIndexOffsets = new long[newDimensions.length];
    int[] newOrder = new int[sortedDims.length];
    int[] dimensionSizes = getDimensionSizes();
    long curIndexOffset = 1;
    for (int i = sortedDims.length - 1; i >= 0; i--) {
      newOrder[currentDimInds.get(sortedDims[i])] = i;
      sortedSizes[i] = dimensionSizes[currentDimInds.get(sortedDims[i])];
      sortedIndexOffsets[i] = curIndexOffset;
      curIndexOffset *= sortedSizes[i];
    }

    double[] resultValues = Arrays.copyOf(values, values.length);
    // Map each key of this into a key of the relabeled tensor.
    long[] resultKeyInts = transformKeyNums(keyNums, indexOffsets, sortedIndexOffsets, newOrder);

    sortOutcomeTable(resultKeyInts, resultValues, 0, values.length);
    return new SparseTensor(sortedDims, sortedSizes, resultKeyInts, resultValues);
  }

  private long[] transformKeyNums(long[] keyNums, long[] indexOffsets, long[] newIndexOffsets,
      int[] newOrder) {
    long[] resultKeyInts = new long[values.length];
    for (int i = 0; i < keyNums.length; i++) {
      long curKey = keyNums[i];
      long newKey = 0;
      for (int j = 0; j < numDimensions(); j++) {
        long dimensionValue = curKey / indexOffsets[j];
        curKey -= dimensionValue * indexOffsets[j];
        newKey += dimensionValue * newIndexOffsets[newOrder[j]];
      }
      resultKeyInts[i] = newKey;
    }
    return resultKeyInts;
  }

  @Override
  public SparseTensor replaceValues(double[] newValues) {
    return new SparseTensor(getDimensionNumbers(), getDimensionSizes(), keyNums, newValues);
  }

  /**
   * Quicksorts the section of {@code keyNums} from {@code startInd} (inclusive)
   * to {@code endInd} (not inclusive), simultaneously swapping the
   * corresponding entries of {@code values}.
   */
  private static void sortOutcomeTable(long[] keyNums, double[] values,
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
  private static void swap(long[] keyNums, double[] values, int i, int j) {
    long keySwap = keyNums[i];
    keyNums[i] = keyNums[j];
    keyNums[j] = keySwap;

    double swapValue = values[i];
    values[i] = values[j];
    values[j] = swapValue;
  }

  @Override
  public long[] getLargestValues(int n) {
    long[] largestKeyIndexes = HeapUtils.findLargestItemIndexes(values, n);
    long[] largestKeyNums = new long[largestKeyIndexes.length];
    for (int i = 0; i < largestKeyIndexes.length; i++) {
      largestKeyNums[i] = keyNums[(int) largestKeyIndexes[i]];
    }
    return largestKeyNums;
  }

  @Override
  public TensorProto toProto() {
    TensorProto.Builder builder = TensorProto.newBuilder();
    builder.setType(TensorProto.TensorType.SPARSE);

    SparseTensorProto.Builder sparseTensorBuilder = builder.getSparseTensorBuilder();
    sparseTensorBuilder.setDimensions(getDimensionProto());
    sparseTensorBuilder.addAllKeyNum(Longs.asList(keyNums));
    sparseTensorBuilder.addAllValue(Doubles.asList(values));
    return builder.build();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<");
    for (int i = 0; i < values.length; i++) {
      if (values[i] != 0.0) {
        sb.append(Arrays.toString(keyNumToDimKey(keyNums[i])));
        sb.append(" : ");
        sb.append(values[i]);
        if (i != values.length - 1) {
          sb.append(", ");
        }
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

  /**
   * Similar to the constructor, except does not require {@code keyNums} to
   * occur in ascending order.
   * 
   * @param dimensionNumbers
   * @param dimensionSizes
   * @param keyNums
   * @param values
   * @return
   */
  public static SparseTensor fromUnorderedKeyValues(int[] dimensionNumbers, int[] dimensionSizes,
      long[] keyNums, double[] values) {
    long[] keyNumsCopy = Arrays.copyOf(keyNums, keyNums.length);
    double[] valuesCopy = Arrays.copyOf(values, values.length);
    sortOutcomeTable(keyNumsCopy, valuesCopy, 0, keyNums.length);

    return new SparseTensor(dimensionNumbers, dimensionSizes, keyNumsCopy, valuesCopy);
  }

  public static SparseTensor singleElement(int[] dimensionNumbers, int[] dimensionSizes, int[] dimKey, double value) {
    long[] keyNums = new long[1];
    double[] values = new double[1];

    keyNums[0] = AbstractTensorBase.dimKeyPrefixToKeyNum(dimKey, dimensionSizes,
        AbstractTensorBase.computeIndexOffsets(dimensionSizes));
    values[0] = value;

    return new SparseTensor(dimensionNumbers, dimensionSizes, keyNums, values);
  }

  /**
   * Creates a {@code SparseTensor} from its serialization as a protocol buffer.
   * 
   * @param proto
   * @return
   */
  public static SparseTensor fromProto(SparseTensorProto proto) {
    Preconditions.checkArgument(proto.hasDimensions());
    int[] dimensionNums = AbstractTensorBase.parseDimensionsFromProto(proto.getDimensions());
    int[] sizes = AbstractTensorBase.parseSizesFromProto(proto.getDimensions());
    Preconditions.checkArgument(dimensionNums.length == sizes.length);

    long[] keyNums = Longs.toArray(proto.getKeyNumList());
    double[] values = Doubles.toArray(proto.getValueList());
    return new SparseTensor(dimensionNums, sizes, keyNums, values);
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