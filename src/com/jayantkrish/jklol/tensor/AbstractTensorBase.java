package com.jayantkrish.jklol.tensor;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.TensorProtos.TensorDimensionProto;

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
  protected final long[] indexOffsets;

  public AbstractTensorBase(int[] dimensions, int[] sizes) {
    Preconditions.checkArgument(dimensions.length == sizes.length);
    for (int i = 0; i < sizes.length; i++) {
      Preconditions.checkArgument(sizes[i] > 0,
          "Cannot create Tensors with zero-size dimensions. Requested dimensions: %s and sizes: %s",
          Ints.asList(dimensions), Ints.asList(sizes));
    }
    this.dimensions = Arrays.copyOf(dimensions, dimensions.length);
    this.sizes = Arrays.copyOf(sizes, sizes.length);

    // Create the data structure for converting dimension keys to integers.
    indexOffsets = new long[sizes.length];
    initializeIndexOffsets(sizes);
  }

  /**
   * Gets the tensor dimensions from their serialized representation. Used by
   * subclasses of this to deserialize themselves.
   * 
   * @return
   */
  protected static int[] parseDimensionsFromProto(TensorDimensionProto proto) {
    int[] dimensionNums = new int[proto.getDimensionCount()];
    for (int i = 0; i < dimensionNums.length; i++) {
      dimensionNums[i] = proto.getDimension(i);
    }
    return dimensionNums;
  }

  /**
   * Gets the tensor dimension sizes from their serialized representation. Used
   * by subclasses of this to deserialize themselves.
   * 
   * @return
   */
  protected static int[] parseSizesFromProto(TensorDimensionProto proto) {
    int[] sizes = new int[proto.getSizeCount()];
    for (int i = 0; i < sizes.length; i++) {
      sizes[i] = proto.getSize(i);
    }
    return sizes;
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
        indexOffsets[i] = indexOffsets[i + 1] * ((long) sizes[i + 1]);
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
  public long[] getDimensionOffsets() {
    return indexOffsets;
  }

  @Override
  public int numDimensions() {
    return dimensions.length;
  }

  /**
   * A keyNum {@code k} is valid for {@code this} if and only if
   * {@code 0 <= k < this.maxKeyNum()}.
   * 
   * @return
   */
  @Override
  public long getMaxKeyNum() {
    return indexOffsets.length == 0 ? 1 : indexOffsets[0] * sizes[0];
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
    int[] sizes = getDimensionSizes();
    long keyNum = 0;
    for (int i = 0; i < keyPrefix.length; i++) {
      Preconditions.checkArgument(keyPrefix[i] >= 0 && keyPrefix[i] < sizes[i],
          "Illegal key prefix.");
      keyNum += ((long) keyPrefix[i]) * indexOffsets[i];
    }
    return keyNum;
  }

  @Override
  public double getByDimKey(int... key) {
    return getByIndex(keyNumToIndex(dimKeyToKeyNum(key)));
  }

  @Override
  public double get(long keyNum) {
    return getByIndex(keyNumToIndex(keyNum));
  }

  @Override
  public double getLogByDimKey(int... key) {
    return getLogByIndex(keyNumToIndex(dimKeyToKeyNum(key)));
  }

  @Override
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
   * Serializes the dimensions and dimension sizes of {@code this} into a
   * protocol buffer.
   * 
   * @return
   */
  protected TensorDimensionProto getDimensionProto() {
    TensorDimensionProto.Builder builder = TensorDimensionProto.newBuilder();
    builder.addAllDimension(Ints.asList(dimensions));
    builder.addAllSize(Ints.asList(sizes));
    return builder.build();
  }
}
