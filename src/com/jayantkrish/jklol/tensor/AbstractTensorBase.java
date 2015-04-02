package com.jayantkrish.jklol.tensor;

import java.io.Serializable;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.ArrayUtils;

/**
 * Common implementations of basic {@link TensorBase} methods.
 * 
 * @author jayantk
 */
public abstract class AbstractTensorBase implements TensorBase, Serializable {

  private static final long serialVersionUID = 251634505015766912L;
  // The dimensions spanned by this tensor, and the size in each
  // dimension.
  private final int[] dimensions;
  private final int[] sizes;

  // Array storing offsets into values which are combined with keys
  // to create an index for the key into values.
  protected final long[] indexOffsets;

  public AbstractTensorBase(int[] dimensions, int[] sizes) {
    Preconditions.checkArgument(dimensions.length == sizes.length,
        "Dimensions and sizes must have same length.");
    for (int i = 0; i < sizes.length; i++) {
      Preconditions.checkArgument(sizes[i] > 0,
          "Cannot create Tensors with zero-size dimensions. Requested dimensions: %s and sizes: %s",
          Ints.asList(dimensions), Ints.asList(sizes));
    }
    this.dimensions = ArrayUtils.copyOf(dimensions, dimensions.length);
    this.sizes = ArrayUtils.copyOf(sizes, sizes.length);

    // Create the data structure for converting dimension keys to
    // integers.
    indexOffsets = computeIndexOffsets(sizes);
  }

  /**
   * Computes an array of index offsets (multipliers) from a set of
   * dimension sizes. The returned array is used to convert int[]
   * tensor keys to longs (and vice versa).
   * 
   * @param sizes
   */
  public static final long[] computeIndexOffsets(int[] sizes) {
    long[] indexOffsets = new long[sizes.length];
    for (int i = sizes.length - 1; i >= 0; i--) {
      if (i == sizes.length - 1) {
        indexOffsets[i] = 1;
      } else {
        indexOffsets[i] = indexOffsets[i + 1] * ((long) sizes[i + 1]);
      }
    }
    return indexOffsets;
  }
  
  /**
   * Converts {@code keyNum} coded using a set of original offsets into
   * a {@code keyNum} using {@code resultOffsets}. This method maps keys
   * between tensors with different dimension orderings.
   *
   * @param keyNum
   * @param originalOffsets
   * @param originalSizes
   * @param resultOffsets
   * @return
   */
  public static final long recodeKeyNum(long keyNum, long[] originalOffsets, int[] originalSizes,
      long[] resultOffsets) {
    long resultKeyNum = 0;
    int numDims = originalOffsets.length;
    for (int j = 0; j < numDims; j++) {
      resultKeyNum += ((keyNum / originalOffsets[j]) % originalSizes[j]) * resultOffsets[j];
    }
    return resultKeyNum;
  }

  /**
   * Merges the given sets of dimensions, verifying that any dimensions in 
   * both sets have the same size.
   *  
   * @param firstDimensionNums
   * @param firstDimensionSizes
   * @param secondDimensionNums
   * @param secondDimensionSizes
   * @return
   */
  public static final DimensionSpec mergeDimensions(int[] firstDimensionNums, int[] firstDimensionSizes,
      int[] secondDimensionNums, int[] secondDimensionSizes) {
    SortedSet<Integer> first = Sets.newTreeSet(Ints.asList(firstDimensionNums));
    SortedSet<Integer> second = Sets.newTreeSet(Ints.asList(secondDimensionNums));
    SortedSet<Integer> all = Sets.newTreeSet(first);
    all.addAll(second);
    
    int[] resultDims = Ints.toArray(all);
    int[] resultSizes = new int[resultDims.length];
    for (int i = 0; i < resultDims.length; i++) {
      int dim = resultDims[i];
      if (first.contains(dim) && second.contains(dim)) {
        int firstIndex = Ints.indexOf(firstDimensionNums, dim);
        int secondIndex = Ints.indexOf(secondDimensionNums, dim);
        int firstSize = firstDimensionSizes[firstIndex];
        int secondSize = secondDimensionSizes[secondIndex];
        Preconditions.checkArgument(firstSize == secondSize,
            "Dimension sizes do not match: dim %s, sizes %s and %s.", dim, firstSize, secondSize);
        resultSizes[i] = firstSize;
      } else if (first.contains(dim)) {
        int firstIndex = Ints.indexOf(firstDimensionNums, dim);
        int firstSize = firstDimensionSizes[firstIndex];
        resultSizes[i] = firstSize;
      } else {
        int secondIndex = Ints.indexOf(secondDimensionNums, dim);
        int secondSize = secondDimensionSizes[secondIndex];
        resultSizes[i] = secondSize;
      }
    }

    return new DimensionSpec(resultDims, resultSizes);
  }

  /**
   * Gets a mapping from dimensions in {@code firstDimensionNums} to their
   * index in {@code secondDimensionNums}. Entries in the alignment are
   * -1 for dimensions in {@code firstDimensionNums} that are not in
   * {@code secondDimensionNums}.
   * 
   * @param firstDimensionNums
   * @param secondDimensionNums
   * @return
   */
  public static final int[] getDimensionAlignment(int[] firstDimensionNums, int[] secondDimensionNums) {
    int[] alignment = new int[firstDimensionNums.length];
    for (int i = 0; i < firstDimensionNums.length; i++) {
      alignment[i] = Ints.indexOf(secondDimensionNums, firstDimensionNums[i]);
    }
    return alignment;
  }

  @Override
  public final int[] getDimensionNumbers() {
    return dimensions;
  }

  @Override
  public final int[] getDimensionSizes() {
    return sizes;
  }

  @Override
  public final long[] getDimensionOffsets() {
    return indexOffsets;
  }

  @Override
  public final int numDimensions() {
    return dimensions.length;
  }

  /**
   * A keyNum {@code k} is valid for {@code this} if and only if
   * {@code 0 <= k < this.maxKeyNum()}.
   * 
   * @return
   */
  @Override
  public final long getMaxKeyNum() {
    return indexOffsets.length == 0 ? 1 : indexOffsets[0] * sizes[0];
  }

  @Override
  public final int indexToPartialDimKey(int index, int dimIndex) {
    return keyNumToPartialDimKey(indexToKeyNum(index), dimIndex);
  }

  @Override
  public final int keyNumToPartialDimKey(long keyNum, int dimIndex) {
    long v = keyNum;
    if (dimIndex > 0) {
      v = v % indexOffsets[dimIndex - 1];
    }
    return (int) (v / indexOffsets[dimIndex]);
  }

  @Override
  public int[] keyNumToDimKey(long keyNum) {
    int[] key = new int[sizes.length];
    keyNumToDimKey(keyNum, key);
    return key;
  }

  @Override
  public void keyNumToDimKey(long keyNum, int[] key) {
    Preconditions.checkArgument(key.length >= sizes.length);

    long curVal = keyNum;
    for (int i = 0; i < sizes.length; i++) {
      key[i] = (int) (curVal / indexOffsets[i]);
      curVal -= key[i] * indexOffsets[i];
    }
  }

  @Override
  public long dimKeyToKeyNum(int[] key) {
    Preconditions.checkArgument(key.length == getDimensionNumbers().length);
    return dimKeyPrefixToKeyNum(key);
  }

  @Override
  public long dimKeyPrefixToKeyNum(int[] keyPrefix) {
    return dimKeyPrefixToKeyNum(keyPrefix, getDimensionSizes(), indexOffsets);
  }

  protected static final long dimKeyPrefixToKeyNum(int[] keyPrefix, int[] sizes, long[] indexOffsets) {
    long keyNum = 0;
    for (int i = 0; i < keyPrefix.length; i++) {
      Preconditions.checkArgument(keyPrefix[i] >= 0 && keyPrefix[i] < sizes[i],
          "Illegal key element: %s. (index: %s, allowed range: 0 to %s)", keyPrefix[i], i, sizes[i]);
      keyNum += ((long) keyPrefix[i]) * indexOffsets[i];
    }
    return keyNum;
  }

  @Override
  public final double getByDimKey(int... key) {
    return get(dimKeyToKeyNum(key));
  }

  public double get(long keyNum) {
    return getByIndex(keyNumToIndex(keyNum));
  }

  @Override
  public final double getLogByDimKey(int... key) {
    return getLog(dimKeyToKeyNum(key));
  }

  public double getLog(long keyNum) {
    return getLogByIndex(keyNumToIndex(keyNum));
  }

  protected int getDimensionIndex(int dimensionNum) {
    for (int i = 0; i < dimensions.length; i++) {
      if (dimensions[i] == dimensionNum) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns {@code true} if the dimensions in {@code otherDims} occur
   * occur at the very end of this tensor's dimension array.
   * 
   * @param otherDims
   * @return
   */
  public boolean areDimensionsRightAligned(int[] otherDims) {
    for (int i = 1; i < otherDims.length + 1; i++) {
      if (otherDims[otherDims.length - i] != dimensions[dimensions.length - i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns {@code true} if the dimensions in {@code otherDims} occur
   * occur at the very beginning of this tensor's dimension array.
   * 
   * @param otherDims
   * @return
   */
  public boolean areDimensionsLeftAligned(int[] otherDims) {
    for (int i = 0; i < otherDims.length; i++) {
      if (otherDims[i] != dimensions[i]) {
        return false;
      }
    }
    return true;
  }
  
  public static class DimensionSpec {
    private final int[] dimensionNums;
    private final int[] dimensionSizes;
    
    public DimensionSpec(int[] dimensionNums, int[] dimensionSizes) {
      this.dimensionNums = Preconditions.checkNotNull(dimensionNums);
      this.dimensionSizes = Preconditions.checkNotNull(dimensionSizes);
    }

    public int[] getDimensionNumbers() {
      return dimensionNums;
    }
    
    public int[] getDimensionSizes() {
      return dimensionSizes;
    }
  }
}
