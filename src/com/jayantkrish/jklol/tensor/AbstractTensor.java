package com.jayantkrish.jklol.tensor;

import java.util.Collection;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
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
  public Tensor maxOutDimensions(int[] dimensionsToEliminate, Backpointers backpointers) {
    return maxOutDimensions(Ints.asList(dimensionsToEliminate), backpointers);
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
    // throw new UnsupportedOperationException("Not yet implemented.");

    if (dimensionsToEliminate.size() == 0) {
      return tensor;
    }

    Tensor minValues = tensor.elementwiseProduct(-1.0).maxOutDimensions(dimensionsToEliminate);
    return tensor.elementwiseAddition(minValues).elementwiseExp().sumOutDimensions(dimensionsToEliminate)
        .elementwiseLog().elementwiseAddition(minValues.elementwiseProduct(-1.0));
  }
  
  /**
   * Default implementation of tensor outer products.
   * 
   * @param first
   * @param second
   * @return
   */
  public static Tensor outerProduct(Tensor first, Tensor second) {
    int[] firstDimNums = first.getDimensionNumbers();
    long[] firstKeyOffsets = first.getDimensionOffsets();
    int[] firstSizes = first.getDimensionSizes();
    int[] secondDimNums = second.getDimensionNumbers();
    long[] secondKeyOffsets = second.getDimensionOffsets();
    int[] secondSizes = second.getDimensionSizes();
    
    // Determine the dimensions of the result tensor and compute
    // the mapping between keys of the input tensors and the result. 
    SortedSet<Integer> resultDimSet = Sets.newTreeSet(Ints.asList(firstDimNums));
    resultDimSet.addAll(Ints.asList(secondDimNums));
    int[] resultDims = Ints.toArray(resultDimSet);
    int[] resultSizes = new int[resultDims.length];
    Preconditions.checkArgument(resultDims.length == firstDimNums.length + secondDimNums.length);

    for (int i = 0; i < resultDims.length; i++) {
      int firstIndex = Ints.indexOf(firstDimNums, resultDims[i]);
      if (firstIndex != -1) {
        resultSizes[i] = firstSizes[firstIndex];
      } else {
        int secondIndex = Ints.indexOf(secondDimNums, resultDims[i]);
        resultSizes[i] = secondSizes[secondIndex];
      }
    }
    long[] resultDimOffsets = AbstractTensorBase.computeIndexOffsets(resultSizes);

    long[] firstResultKeyOffsets = new long[firstDimNums.length];
    long[] secondResultKeyOffsets = new long[secondDimNums.length];
    for (int i = 0; i < firstDimNums.length; i++) {
      int resultIndex = Ints.indexOf(resultDims, firstDimNums[i]);
      firstResultKeyOffsets[i] = resultDimOffsets[resultIndex];
    }

    for (int i = 0; i < secondDimNums.length; i++) {
      int resultIndex = Ints.indexOf(resultDims, secondDimNums[i]);
      secondResultKeyOffsets[i] = resultDimOffsets[resultIndex];
    }

    // Merge the keys and values of both tensors using the computed mapping.
    int numKeys = first.size() * second.size();
    long[] resultKeyNums = new long[numKeys];
    double[] resultValues = new double[numKeys];
    int resultIndex = 0;
    int firstNumKeys = first.size();
    int secondNumKeys = second.size();
    for (int i = 0; i < firstNumKeys; i++) {
      long firstCurKeyNum = first.indexToKeyNum(i); 
      long resultKeyNum = AbstractTensorBase.recodeKeyNum(firstCurKeyNum, firstKeyOffsets, firstSizes,
          firstResultKeyOffsets);
      double firstValue = first.getByIndex(i);
      for (int k = 0; k < secondNumKeys; k++) {
        long secondKeyNum = second.indexToKeyNum(k);
        long secondResultKeyNum = AbstractTensorBase.recodeKeyNum(secondKeyNum, secondKeyOffsets, 
            secondSizes, secondResultKeyOffsets);
        
        resultKeyNums[resultIndex] = resultKeyNum + secondResultKeyNum;
        resultValues[resultIndex] = firstValue * second.getByIndex(k);
        resultIndex++;
      }
    }

    Preconditions.checkState(resultIndex == numKeys);
    return SparseTensor.fromUnorderedKeyValuesNoCopy(resultDims, resultSizes, resultKeyNums, resultValues);
  }
}
