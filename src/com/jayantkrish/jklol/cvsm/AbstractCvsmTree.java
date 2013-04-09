package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.Tensor;

public abstract class AbstractCvsmTree implements CvsmTree {

  private final Tensor value;
  
  public AbstractCvsmTree(Tensor value) {
    this.value = Preconditions.checkNotNull(value);
  }
  
  @Override
  public Tensor getValue() {
    return value;
  }
}
