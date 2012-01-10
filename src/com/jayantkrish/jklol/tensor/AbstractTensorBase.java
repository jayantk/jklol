package com.jayantkrish.jklol.tensor;

import java.util.Arrays;

import com.google.common.base.Preconditions;

/**
 * Common implementations of basic {@link TensorBase} methods.
 * 
 * @author jayantk
 */
public abstract class AbstractTensorBase implements TensorBase {
  
  // The dimensions spanned by this tensor, and the size in each dimension.
  private final int[] dimensions;
  private final int[] sizes;
  
  // Array storing offsets into values which are combined with keys
  // to create an index for the key into values.
  protected final int[] indexOffsets;
  
  public AbstractTensorBase(int[] dimensions, int[] sizes) {
    Preconditions.checkArgument(dimensions.length == sizes.length);
    this.dimensions = Arrays.copyOf(dimensions, dimensions.length);
    this.sizes = Arrays.copyOf(sizes, sizes.length);
    
    // Create the data structure for converting dimension keys to integers.
    indexOffsets = new int[sizes.length];
    initializeIndexOffsets(sizes);
  }
  
  /**
   * Initializes {@code this.indexOffsets} array based on the known dimension
   * sizes.
   * 
   * @param sizes
   */
  private void initializeIndexOffsets(int[] sizes) {
    for (int i = sizes.length - 1; i >= 0; i--) {
      if (i == sizes.length - 1) {
        indexOffsets[i] = 1;
      } else {
        indexOffsets[i] = indexOffsets[i + 1] * sizes[i + 1];
      }
    }
  }
  
  @Override
  public int[] getDimensionNumbers() {
    return dimensions;
  }

  @Override
  public int[] getDimensionSizes() {
    return sizes;
  }
  
  @Override
  public int numDimensions() {
    return dimensions.length;
  }
  
  /**
   * A keyInt {@code k} is valid for {@code this} if and only if {@code 0 <= k < this.maxKeyInt()}.
   *  
   * @return
   */
  public int maxKeyInt() {
    return indexOffsets.length == 0 ? 1 : indexOffsets[0] * sizes[0]; 
  }
  
  @Override
  public int[] keyIntToDimKey(int keyInt) {
    int[] key = new int[sizes.length];
    int curVal = keyInt;
    for (int i = 0; i < sizes.length; i++) {
      key[i] = curVal / indexOffsets[i];
      curVal -= key[i] * indexOffsets[i];
    }
    return key;
  }
  
  @Override
  public int dimKeyToKeyInt(int[] key) {
    Preconditions.checkArgument(key.length == getDimensionNumbers().length);

    int[] sizes = getDimensionSizes();
    int index = 0;
    for (int i = 0; i < key.length; i++) {
      Preconditions.checkArgument(key[i] >= 0 && key[i] < sizes[i]);
      index += key[i] * indexOffsets[i];
    }
    return index;    
  }
  
  @Override
  public double getByDimKey(int... key) {
    return getByIndex(keyIntToIndex(dimKeyToKeyInt(key)));
  }
  
  @Override
  public double get(int keyInt) {
    return getByIndex(keyIntToIndex(keyInt));
  }  
  
  protected int getDimensionIndex(int dimensionNum) {
    for (int i = 0; i < dimensions.length; i++) {
      if (dimensions[i] == dimensionNum) {
        return i;
      }
    }
    return -1;
  }
}
