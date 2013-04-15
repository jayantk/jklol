package com.jayantkrish.jklol.cvsm;

import com.google.common.collect.BiMap;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class TensorLowRankTensor extends AbstractLowRankTensor {
  
  private final Tensor tensor;

  public TensorLowRankTensor(Tensor tensor) {
    super(tensor.getDimensionNumbers(), tensor.getDimensionSizes());
    this.tensor = tensor;
  }
  
  public static TensorLowRankTensor zero(int[] dimensionNumbers, int[] dimensionSizes) {
    return new TensorLowRankTensor(SparseTensor.empty(dimensionNumbers, dimensionSizes));
  }

  @Override
  public Tensor getTensor() {
    return tensor;
  }

  @Override
  public LowRankTensor relabelDimensions(BiMap<Integer, Integer> relabeling) {
    return new TensorLowRankTensor(tensor.relabelDimensions(relabeling));
  }

  @Override
  public LowRankTensor innerProduct(LowRankTensor other) {
    return new TensorLowRankTensor(tensor.innerProduct(other.getTensor()));
  }

  @Override
  public LowRankTensor elementwiseProduct(double value) {
    return new TensorLowRankTensor(tensor.elementwiseProduct(value));
  }
}
