package com.jayantkrish.jklol.tensor;

import java.util.Iterator;

import com.google.common.base.Preconditions;

public class DenseTensorBase extends AbstractTensorBase {
  
  private final int numEntries;
  // Array storing offsets into values which are combined with keys
  // to create an index for the key into values.
  private final int[] indexOffsets;
  
  // Stores the values of each key in this. Accessible to subclasses for
  // fast mathematical operations.
  protected final double[] values;

  /**
   * Creates a tensor with an uninitialized values array.
   * 
   * @param dimensions
   * @param sizes
   */
  public DenseTensorBase(int[] dimensions, int[] sizes) {
    super(dimensions, sizes);
    
    indexOffsets = new int[sizes.length];
    int size = 1;
    for (int i = sizes.length - 1; i >= 0; i--) {
      size *= sizes[i];      
      if (i == sizes.length - 1) {
        indexOffsets[i] = 1;
      } else {
        indexOffsets[i] = indexOffsets[i + 1] * sizes[i + 1]; 
      }
    }
    values = new double[size];
    numEntries = size;
  }
 
  @Override
  public int size() {
    return numEntries;
  }

  @Override
  public double get(int[] key) {
    return values[getIndex(key)];
  }

  @Override
  public Iterator<int[]> keyIterator() {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
  
  /**
   * Gets the index into {@code values} which stores the value for {@code key}.
   * @param key
   * @return
   */
  protected int getIndex(int[] key) {
    Preconditions.checkArgument(key.length == getDimensionNumbers().length);
    
    int[] sizes = getDimensionSizes();
    int index = 0;
    for (int i = 0; i < key.length; i++) {
      Preconditions.checkArgument(key[i] >= 0 && key[i] < sizes[i]);
      index += key[i] * indexOffsets[i];
    }
    return index;
  }
}
