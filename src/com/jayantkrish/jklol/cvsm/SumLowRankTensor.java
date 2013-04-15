package com.jayantkrish.jklol.cvsm;

import com.google.common.collect.BiMap;
import com.jayantkrish.jklol.tensor.Tensor;

public class SumLowRankTensor extends AbstractLowRankTensor {
  
  private final LowRankTensor[] tensors;

  public SumLowRankTensor(int[] dimensionNums, int[] dimensionSizes) {
    super(dimensionNums, dimensionSizes);
    // TODO Auto-generated constructor stub
  }

  @Override
  public Tensor getTensor() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public LowRankTensor relabelDimensions(BiMap<Integer, Integer> relabeling) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public LowRankTensor innerProduct(LowRankTensor other) {
    LowRankTensor[] results = new LowRankTensor[tensors.length];
    for (int i = 0; i < tensors.length; i++) {
      results[i] = tensors[i].innerProduct(other);
    }
    return new SumLowRankTensor(results);
  }

  @Override
  public LowRankTensor outerProduct(LowRankTensor other) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public LowRankTensor elementwiseAddition(LowRankTensor other) {
    // TODO Auto-generated method stub
    return null;
  }

}
