package com.jayantkrish.jklol.tensor;

import java.util.Arrays;

public abstract class AbstractTensorBase implements TensorBase {
  
  // The dimensions spanned by this tensor, and the size in each dimension.
  private int[] dimensions;
  private int[] sizes;
  
  public AbstractTensorBase(int[] dimensions, int[] sizes) {
    this.dimensions = Arrays.copyOf(dimensions, dimensions.length);
    this.sizes = Arrays.copyOf(sizes, sizes.length);
  }
  
  @Override
  public int[] getDimensionNumbers() {
    return dimensions;
  }

  @Override
  public int[] getDimensionSizes() {
    return sizes;
  }
}
