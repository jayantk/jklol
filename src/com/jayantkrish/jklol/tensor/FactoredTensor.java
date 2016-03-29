package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

/**
 * A tensor represented as a product of tensors.
 * 
 * @author jayantk
 */
public class FactoredTensor extends AbstractTensor {
  private static final long serialVersionUID = 2L;

  // The elements which are multiplied to get the value of this tensor.
  private final Tensor[] tensors;
  // Stores the subset of dimensions which are used to index each tensor.
  // Offsets are set to zero for dimensions which are not used.
  private final long[] tensorDimensionOffsets;
  private final int numDimensions;

  public FactoredTensor(int[] dimensions, int[] sizes, Tensor[] tensors) {
    super(dimensions, sizes);
    this.tensors = tensors;
  
    this.numDimensions = dimensions.length;
    this.tensorDimensionOffsets = new long[tensors.length * numDimensions];
    Arrays.fill(tensorDimensionOffsets, 0L);
    for (int i = 0; i < tensors.length; i++) {
      Tensor tensor = tensors[i];
      int[] tensorDims = tensor.getDimensionNumbers();
      long[] tensorOffsets = tensor.getDimensionOffsets();
      int numDims = 0;
      for (int j = 0; j < numDimensions; j++) {
        int dimIndex = Ints.indexOf(tensorDims, dimensions[j]);
        if (dimIndex != -1) {
          tensorDimensionOffsets[(i * numDimensions) + j] = tensorOffsets[dimIndex];
          numDims++;
        }
      }
      Preconditions.checkState(numDims == tensorDims.length);
    }
  }

  @Override
  public Tensor slice(int[] dimensionNumbers, int[] keys) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor retainKeys(Tensor indicatorTensor) {
    return this;
  }
  
  @Override
  public Tensor findKeysLargerThan(double thresholdValue) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor elementwiseProduct(Tensor other) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor innerProduct(Tensor other) {
    throw new UnsupportedOperationException("Not implemented.");
  }
  
  @Override
  public Tensor matrixInnerProduct(Tensor other) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor outerProduct(Tensor other) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor elementwiseAddition(Tensor other) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor elementwiseAddition(double value) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor elementwiseMaximum(Tensor other) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor elementwiseInverse() {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor elementwiseSqrt() {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor elementwiseLog() {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public DenseTensor elementwiseLogSparse() {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor elementwiseExp() {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public DenseTensor elementwiseExpSparse() {
    throw new UnsupportedOperationException("Not implemented.");
  }
  
  @Override
  public Tensor elementwiseTanh() {
    throw new UnsupportedOperationException("Not implemented.");
  }
  
  @Override
  public Tensor elementwiseAbs() {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor elementwiseLaplaceSigmoid(double smoothness) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor softThreshold(double threshold) {
    throw new UnsupportedOperationException("Not implemented.");
  }
  
  @Override
  public Tensor getEntriesLargerThan(double threshold) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor sumOutDimensions(Collection<Integer> dimensionsToEliminate) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate, Backpointers backpointers) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor relabelDimensions(int[] newDimensions) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor relabelDimensions(Map<Integer, Integer> relabeling) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor replaceValues(double[] values) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public int getNearestIndex(long keyNum) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public double[] getValues() {
    throw new UnsupportedOperationException("Not implemented.");
  }
  
  @Override
  public TensorHash toHash() {
    TensorHash[] hashes = new TensorHash[tensors.length];
    for (int i = 0; i < tensors.length; i++) {
      hashes[i] = tensors[i].toHash();
    }
    return new FactoredTensorHash(hashes, getDimensionOffsets(), getMaxKeyNum(),
        tensorDimensionOffsets, numDimensions);
  }

  @Override
  public int size() {
    int size = 0;
    for (Tensor tensor : tensors) {
      size += tensor.size();
    }
    return size;
  }

  @Override
  public final double get(long keyNum) {
    long[] dimensionOffsets = getDimensionOffsets();
    double prob = 1.0;
    for (int i = 0; i < tensors.length; i++) {
      Tensor tensor = tensors[i];
      long tensorKeyNum = 0;
      for (int j = 0; j < numDimensions; j++) {
        long prevModulo = (j == 0) ? getMaxKeyNum() : dimensionOffsets[j - 1];
        tensorKeyNum += tensorDimensionOffsets[(i * numDimensions) + j]
            * ((keyNum % prevModulo) / dimensionOffsets[j]);
      }
      prob *= tensor.get(tensorKeyNum);
    }
    return prob;
  }

  @Override
  public final double getLog(long keyNum) {
    long[] dimensionOffsets = getDimensionOffsets();
    double prob = 0.0;
    for (int i = 0; i < tensors.length; i++) {
      Tensor tensor = tensors[i];
      long tensorKeyNum = 0;
      for (int j = 0; j < numDimensions; j++) {
        long prevModulo = (j == 0) ? getMaxKeyNum() : dimensionOffsets[j - 1];
        tensorKeyNum += tensorDimensionOffsets[(i * numDimensions) + j]
            * ((keyNum % prevModulo) / dimensionOffsets[j]); 
      }
      prob += tensor.getLog(tensorKeyNum);
    }
    return prob;
  }

  @Override
  public double getByIndex(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getLogByIndex(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int keyNumToIndex(long keyNum) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long indexToKeyNum(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<KeyValue> keyValueIterator() {
    return new KeyToKeyValueIterator(new IntegerArrayIterator(getDimensionSizes(), new int[0]),
        this);
  }

  @Override
  public Iterator<KeyValue> keyValuePrefixIterator(int[] keyPrefix) {
    return new KeyToKeyValueIterator(IntegerArrayIterator.createFromKeyPrefix(getDimensionSizes(), keyPrefix),
        this);
  }

  @Override
  public double getL2Norm() {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public double getTrace() {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public long[] getLargestValues(int n) {
    throw new UnsupportedOperationException("Not implemented.");
  }
  
  private static class FactoredTensorHash implements TensorHash {
    private static final long serialVersionUID = 1L;

    // The elements which are multiplied to get the value of this tensor.
    private final TensorHash[] tensors;
    private final long[] dimensionOffsets;
    private final long maxKeyNum;
    
    // Stores the subset of dimensions which are used to index each tensor.
    // Offsets are set to zero for dimensions which are not used.
    private final long[] tensorDimensionOffsets;
    private final int numDimensions;
    
    public FactoredTensorHash(TensorHash[] tensors, long[] dimensionOffsets, long maxKeyNum,
        long[] tensorDimensionOffsets, int numDimensions) {
      this.tensors = tensors;
      this.dimensionOffsets = dimensionOffsets;
      this.maxKeyNum = maxKeyNum;
      this.tensorDimensionOffsets = tensorDimensionOffsets;
      this.numDimensions = numDimensions;
    }

    @Override
    public double get(long keyNum) {
      double prob = 1.0;
      for (int i = 0; i < tensors.length; i++) {
        TensorHash tensor = tensors[i];
        long tensorKeyNum = 0;
        for (int j = 0; j < numDimensions; j++) {
          long prevModulo = (j == 0) ? maxKeyNum : dimensionOffsets[j - 1];
          tensorKeyNum += tensorDimensionOffsets[(i * numDimensions) + j]
              * ((keyNum % prevModulo) / dimensionOffsets[j]);
        }
        prob *= tensor.get(tensorKeyNum);
      }
      return prob;
    }
  }
}
