package com.jayantkrish.jklol.cvsm.tree;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cvsm.lrt.LowRankTensor;

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
