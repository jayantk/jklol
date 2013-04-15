package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;

public abstract class AbstractCvsmTree implements CvsmTree {

  private final LowRankTensor value;
  
  public AbstractCvsmTree(LowRankTensor value) {
    this.value = Preconditions.checkNotNull(value);
  }
  
  @Override
  public LowRankTensor getValue() {
    return value;
  }
}
