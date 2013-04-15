package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;

public abstract class AbstractLowRankTensor implements LowRankTensor {
  private static final long serialVersionUID = 1L;
  
  private final int[] dimensionNums;
  private final int[] dimensionSizes;
  
  public AbstractLowRankTensor(int[] dimensionNums, int[] dimensionSizes) {
    this.dimensionNums = Preconditions.checkNotNull(dimensionNums);
    this.dimensionSizes = Preconditions.checkNotNull(dimensionSizes);
  }

  @Override
  public int[] getDimensionNumbers() {
    return dimensionNums;
  }

  @Override
  public int[] getDimensionSizes() {
    return dimensionSizes;
  }
}
