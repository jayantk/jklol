package com.jayantkrish.jklol.tensor;

import java.util.Collection;
import java.util.List;
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
  
  @Override
  public double innerProductScalar(Tensor other) {
    // Note: you can easily implement this using 
    // this.innerProduct(other).getByDimKey();
    throw new UnsupportedOperationException("Not yet implemented. Note that you can use" +
        "this.innerProduct(other).getByDimKey() to achieve the same result less efficiently");
  }

  public static Tensor logSumOutDimensions(Tensor tensor, Collection<Integer> dimensionsToEliminate) {
    if (dimensionsToEliminate.size() == 0) {
      return tensor;
    }

    Tensor minValues = tensor.elementwiseProduct(-1.0).maxOutDimensions(dimensionsToEliminate);
    return tensor.elementwiseAddition(minValues).elementwiseExp().sumOutDimensions(dimensionsToEliminate)
        .elementwiseLog().elementwiseAddition(minValues.elementwiseProduct(-1.0));
  }

  public static Tensor innerProduct(Tensor first, Tensor second, TensorFactory factory) {
    int myInd = 0;
    int mySize = first.size();
    int otherSize = second.size();

    // Figure out which dimensions of first are alignable to second.
    int[] myDimensionNums = first.getDimensionNumbers();
    int[] myDimensionSizes = first.getDimensionSizes();
    long[] myDimensionOffsets = first.getDimensionOffsets();
    List<Integer> myDimensionNumsList = Ints.asList(myDimensionNums);
    int[] otherDimensionNums = second.getDimensionNumbers();
    int[] otherDimensionSizes = second.getDimensionSizes();
    long[] otherDimensionOffsets = second.getDimensionOffsets();
    List<Integer> otherDimensionNumsList = Ints.asList(otherDimensionNums);

    int firstAlignedDim = -1;
    int numOtherDims = 0;
    for (int i = 0; i < myDimensionNums.length; i++) {
      if (myDimensionNums[i] == otherDimensionNums[numOtherDims]) {
        if (numOtherDims == 0) {
          firstAlignedDim = i;
        }

        Preconditions.checkArgument(myDimensionSizes[i] == otherDimensionSizes[numOtherDims]);
        Preconditions.checkArgument(numOtherDims == 0 || myDimensionNums[i - 1] == otherDimensionNums[numOtherDims - 1],
            "Dimensions of second must be contiguous in first. Got %s and %s.", myDimensionNumsList, otherDimensionNumsList);

        numOtherDims++;
      }
    }
    Preconditions.checkArgument(numOtherDims > 0);
    
    // This is an inclusive index.
    int lastAlignedDim = firstAlignedDim + numOtherDims - 1;

    // Use the alignment of dimensions to compute a mapping between
    // the keys of the two tensors and the result.
    long keyNumDivisor = myDimensionOffsets[lastAlignedDim];
    long keyNumModulo = firstAlignedDim != 0 ? myDimensionOffsets[firstAlignedDim - 1] : first.getMaxKeyNum();

    // otherKeyNumOffset partitions the keynums of other into two
    // parts. Note that numOtherDims must be > 0.
    long otherKeyNumOffset = otherDimensionOffsets[numOtherDims - 1];
    
    // This check is not necessary, but makes it easier to construct the
    // dimensions of the result. Really, the result dimensions produced
    // from second need to be contiguous when merged with the dimensions
    // produced by first.
    Preconditions.checkArgument(lastAlignedDim == myDimensionNums.length - 1 ||
        numOtherDims == otherDimensionNums.length ||
        myDimensionNums[lastAlignedDim + 1] > otherDimensionNums[otherDimensionNums.length - 1]);
    
    int numResultDims = (myDimensionNums.length - numOtherDims) + 
        (otherDimensionNums.length - numOtherDims);
    int[] resultDimensionNums = new int[numResultDims];
    int[] resultDimensionSizes = new int[numResultDims];
    
    for (int i = 0; i < firstAlignedDim; i++) {
      resultDimensionNums[i] = myDimensionNums[i];
      resultDimensionSizes[i] = myDimensionSizes[i];
    }
    int numOtherUnalignedDims = otherDimensionNums.length - numOtherDims;
    for (int i = 0; i < numOtherUnalignedDims; i++) {
      resultDimensionNums[i + firstAlignedDim] = otherDimensionNums[i + numOtherDims];
      resultDimensionSizes[i + firstAlignedDim] = otherDimensionSizes[i + numOtherDims];
    }
    for (int i = 0; i < myDimensionNums.length - (lastAlignedDim + 1); i++) {
      resultDimensionNums[i + firstAlignedDim + numOtherDims] = myDimensionNums[i + lastAlignedDim + 1];
      resultDimensionSizes[i + firstAlignedDim + numOtherDims] = myDimensionSizes[i + lastAlignedDim + 1];
    }
    TensorBuilder builder = factory.getBuilder(resultDimensionNums, resultDimensionSizes);
    long[] resultDimensionOffsets = builder.getDimensionOffsets();
    long resultPrefixOffset = firstAlignedDim != 0 ? resultDimensionOffsets[firstAlignedDim - 1] : 0;
    long resultMiddleOffset = resultDimensionOffsets[firstAlignedDim + numOtherUnalignedDims - 1];
    long resultSuffixOffset = 1L;

    while (myInd < mySize) {
      long myKeyNum = first.indexToKeyNum(myInd);
      double myValue = first.getByIndex(myInd);

      // myKeyNum has three parts: the parts before, in, and after 
      // the dimensions involved in the inner product.
      long myKeyNumPrefix = myKeyNum / keyNumModulo;
      long myKeyNumMiddle = (myKeyNum % keyNumModulo) / keyNumDivisor;
      long myKeyNumSuffix = myKeyNum % keyNumDivisor;

      // Iterate over the dimensions of second which are not
      // involved in the inner product with first.
      long otherKeyNumPrefix = myKeyNumMiddle * otherKeyNumOffset;
      long otherMaxKeyNum = otherKeyNumPrefix + otherKeyNumOffset;
      int otherIndex = second.getNearestIndex(otherKeyNumPrefix);
      while (otherIndex < otherSize) {
        long otherKeyNum = second.indexToKeyNum(otherIndex);
        if (otherKeyNum >= otherMaxKeyNum) {
          break;
        }

        long otherResultKeyNum = otherKeyNum % otherKeyNumOffset;
        long resultKeyNum = (myKeyNumPrefix * resultPrefixOffset) + 
            (otherResultKeyNum * resultMiddleOffset) + (myKeyNumSuffix * resultSuffixOffset); 

        double value = myValue * second.getByIndex(otherIndex);
        builder.incrementEntryByKeyNum(value, resultKeyNum);

        otherIndex++;
      }

      myInd++;
    }

    return builder.build();
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
