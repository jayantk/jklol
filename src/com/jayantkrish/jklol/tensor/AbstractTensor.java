package com.jayantkrish.jklol.tensor;

import java.util.Collection;

import com.google.common.primitives.Ints;

/**
 * Common implementations of {@code Tensor} methods.
 *
 * @author jayantk
 */
public abstract class AbstractTensor extends AbstractTensorBase implements Tensor {

  private static final long serialVersionUID = -2075571922466993976L;

  public AbstractTensor(int[] dimensions, int[] sizes) {
    super(dimensions, sizes);
  }
  
  @Override
  public Tensor elementwiseProduct(Collection<Tensor> others) {
    Tensor result = this;
    for (Tensor other : others) {
      result = result.elementwiseProduct(other);
    }
    return result;
  }
  
  @Override
  public Tensor elementwiseProduct(double value) {
    return elementwiseProduct(SparseTensor.getScalarConstant(value));
  }
  
  @Override
  public Tensor sumOutDimensions(int... dimensionsToEliminate) {
    return sumOutDimensions(Ints.asList(dimensionsToEliminate));
  }
  
  @Override
  public Tensor maxOutDimensions(int[] dimensionsToEliminate) {
    return maxOutDimensions(Ints.asList(dimensionsToEliminate));
  }
  
  @Override
  public Tensor logSumOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return AbstractTensor.logSumOutDimensions(this, dimensionsToEliminate);
  }
  
  @Override
  public Tensor logSumOutDimensions(int[] dimensionsToEliminate) {
    return logSumOutDimensions(Ints.asList(dimensionsToEliminate));
  }

  public static Tensor logSumOutDimensions(Tensor tensor, Collection<Integer> dimensionsToEliminate) {
    int eliminatedDimensionSize = 1;
    int[] nums = tensor.getDimensionNumbers();
    int[] sizes = tensor.getDimensionSizes();
    
    Tensor minValues = tensor.elementwiseProduct(-1.0).maxOutDimensions(dimensionsToEliminate);    
    Tensor replicatedMinValues = minValues.elementwiseProduct(tensor.getNumKeysInDimensions(dimensionToEliminate));
    
    return tensor.sumOutDimensions(dimensionsToEliminate).elementwiseAddition(replicatedMinValues)
        .elementwiseExp().elementwiseAddition(1.0).elementwiseLog().elementwiseAddition(minValues);
  }
}
