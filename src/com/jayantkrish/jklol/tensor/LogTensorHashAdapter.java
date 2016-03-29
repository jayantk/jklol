package com.jayantkrish.jklol.tensor;

import com.google.common.base.Preconditions;

public class LogTensorHashAdapter implements TensorHash {
  private static final long serialVersionUID = 1L;

  private final TensorHash tensorHash;

  public LogTensorHashAdapter(TensorHash tensorHash) {
    this.tensorHash = Preconditions.checkNotNull(tensorHash);
  }

  @Override
  public double get(long keyNum) {
    return Math.exp(tensorHash.get(keyNum));
  }
}
