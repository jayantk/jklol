package com.jayantkrish.jklol.tensor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

public class SparseLogSpaceTensorAdapter extends AbstractTensor {
  
  private static final long serialVersionUID = 1L;
  
  private final Tensor logWeights;

  public SparseLogSpaceTensorAdapter(Tensor logWeights) {
    super(logWeights.getDimensionNumbers(), logWeights.getDimensionSizes());
    this.logWeights = Preconditions.checkNotNull(logWeights);
  }
  
  @Override
  public Tensor slice(int[] dimensionNumbers, int[] keys) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor retainKeys(Tensor indicatorTensor) {
    return new SparseLogSpaceTensorAdapter(logWeights.retainKeys(indicatorTensor));
  }
  
  @Override
  public Tensor findKeysLargerThan(double thresholdValue) {
    return logWeights.findKeysLargerThan(Math.log(thresholdValue));
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
  public Tensor elementwiseExp() {
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
  public int size() {
    // The size of the tensor is the number of nonzero values in the tensor.
    // However, this tensor may potentially have more than Integer.MAX_VALUE
    // nonzero values due to the log space representation. Returning
    // Integer.MAX_VALUE shouldn't be a problem, given that the values in 
    // this tensor cannot be retrieved using indexes.
    long maxKeyNum = logWeights.getMaxKeyNum();
    return (maxKeyNum > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) maxKeyNum;
  }

  @Override
  public double get(long keyNum) {
    return Math.exp(logWeights.get(keyNum));
  }

  @Override
  public double getLog(long keyNum) {
    return logWeights.get(keyNum);
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
}
