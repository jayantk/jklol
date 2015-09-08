package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

/**
 * A tensor which stores all weights in logarithmic space. This transformation
 * allows the tensor to be used in computations with a much larger range of
 * weights, but comes at a cost of numerical precision.
 * 
 * Most mathematical operations on {@code LogSpaceTensorAdapter} are performed
 * in log space. If the passed-in tensor (to binary operations) is not
 * represented in log space, it is automatically converted to log space before
 * the operation. Addition operations are the only exception to this rule.
 * 
 * @author jayantk
 */
public class LogSpaceTensorAdapter extends AbstractTensor {

  private static final long serialVersionUID = 8713086123218790186L;
  
  private final Tensor logWeights;

  public LogSpaceTensorAdapter(Tensor logWeights) {
    super(logWeights.getDimensionNumbers(), logWeights.getDimensionSizes());
    this.logWeights = logWeights;
    // This tensor assumes that indexes are the same as keynums.
    Preconditions.checkArgument(logWeights.getMaxKeyNum() < Integer.MAX_VALUE);
  }

  @Override
  public int size() {
    long size = logWeights.getMaxKeyNum();
    return (int) size;
  }

  @Override
  public double getByIndex(int index) {
    return Math.exp(logWeights.get((long) index));
  }

  @Override
  public double getLogByIndex(int index) {
    return logWeights.get((long) index);
  }

  @Override
  public int keyNumToIndex(long keyNum) {
    return (int) keyNum;
  }

  @Override 
  public long indexToKeyNum(int index) {
    return (long) index;
  }

  @Override
  public Iterator<KeyValue> keyValueIterator() {
    return new KeyToKeyValueIterator(new IntegerArrayIterator(logWeights.getDimensionSizes(), 
        new int[0]), this);
  }

  @Override
  public Iterator<KeyValue> keyValuePrefixIterator(int[] keyPrefix) {
    return new KeyToKeyValueIterator(IntegerArrayIterator.createFromKeyPrefix(
        getDimensionSizes(), keyPrefix), this);
  }

  @Override
  public double getL2Norm() {
    throw new UnsupportedOperationException("Not implemented");
  }
  
  @Override
  public double getTrace() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Tensor slice(int[] dimensionNumbers, int[] keys) {
    return new LogSpaceTensorAdapter(logWeights.slice(dimensionNumbers, keys));
  }

  @Override
  public Tensor retainKeys(Tensor indicatorTensor) {
    return new LogSpaceTensorAdapter(logWeights.retainKeys(indicatorTensor));
  }
  
  @Override
  public Tensor findKeysLargerThan(double thresholdValue) {
    return logWeights.findKeysLargerThan(Math.log(thresholdValue));
  }

  @Override
  public Tensor elementwiseProduct(Tensor other) {
    if (!(logWeights instanceof SparseTensor) || 
        Arrays.equals(other.getDimensionNumbers(), logWeights.getDimensionNumbers())) {
      return new LogSpaceTensorAdapter(logWeights.elementwiseAddition(other.elementwiseLog()));
    } else {
      // This code hacks around the fact that sparse tensors don't support
      // elementwise addition with replicated dimensions.
      return logWeights.elementwiseExp().elementwiseProduct(other);
    }
  }

  @Override
  public Tensor innerProduct(Tensor other) {
    Tensor product = logWeights.elementwiseAddition(other.elementwiseLog());
    return (new LogSpaceTensorAdapter(product)).sumOutDimensions(other.getDimensionNumbers());
  }
  
  @Override
  public Tensor matrixInnerProduct(Tensor other) {
    throw new UnsupportedOperationException("Not implemented.");
  }
  
  @Override
  public Tensor outerProduct(Tensor other) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public Tensor elementwiseAddition(Tensor other) {
    return logWeights.elementwiseExp().elementwiseAddition(other);
  }
  
  @Override
  public Tensor elementwiseAddition(double value) {
    return logWeights.elementwiseExp().elementwiseAddition(value);
  }

  @Override
  public Tensor elementwiseMaximum(Tensor other) {
    return new LogSpaceTensorAdapter(logWeights.elementwiseMaximum(other.elementwiseLog()));
  }

  @Override
  public Tensor elementwiseInverse() {
    return new LogSpaceTensorAdapter(logWeights.elementwiseProduct(-1.0));
  }
  
  @Override
  public Tensor elementwiseSqrt() {
    return new LogSpaceTensorAdapter(logWeights.elementwiseProduct(0.5));
  }

  @Override
  public Tensor elementwiseLog() {
    return logWeights;
  }
  
  @Override
  public Tensor elementwiseLogSparse() {
    return elementwiseLog();
  }

  @Override
  public Tensor elementwiseExp() {
    return new LogSpaceTensorAdapter(logWeights.elementwiseExp());
  }
  
  @Override
  public Tensor elementwiseExpSparse() {
    return elementwiseExp();
  }
  
  @Override
  public Tensor elementwiseTanh() {
    return logWeights.elementwiseExp().elementwiseTanh();
  }

  @Override
  public Tensor elementwiseAbs() {
    return logWeights.elementwiseExp().elementwiseAbs();
  }
  
  @Override
  public Tensor elementwiseLaplaceSigmoid(double smoothness) {
    return logWeights.elementwiseExp().elementwiseLaplaceSigmoid(smoothness);
  }

  @Override
  public Tensor softThreshold(double threshold) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
  
  @Override
  public Tensor getEntriesLargerThan(double threshold) {
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Tensor sumOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return logWeights.elementwiseExp().sumOutDimensions(dimensionsToEliminate);
  }

  @Override
  public Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate) {
    return new LogSpaceTensorAdapter(logWeights.maxOutDimensions(dimensionsToEliminate));
  }
  
  @Override
  public Tensor maxOutDimensions(Collection<Integer> dimensionsToEliminate, Backpointers backpointers) {
    return new LogSpaceTensorAdapter(logWeights.maxOutDimensions(dimensionsToEliminate, backpointers));
  }

  @Override
  public Tensor relabelDimensions(int[] newDimensions) {
    return new LogSpaceTensorAdapter(logWeights.relabelDimensions(newDimensions));
  }

  @Override
  public Tensor relabelDimensions(Map<Integer, Integer> relabeling) {
    return new LogSpaceTensorAdapter(logWeights.relabelDimensions(relabeling));
  }

  @Override
  public Tensor replaceValues(double[] values) {
    // This method is unsupported because its behavior will be bizarre:
    // replacing the values of the underlying tensor means that values are
    // interpreted as log probabilities (instead of probabilities).
    throw new UnsupportedOperationException();
  }

  @Override
  public int getNearestIndex(long keyNum) {
    return logWeights.getNearestIndex(keyNum);
  }

  @Override
  public double[] getValues() {
    return logWeights.elementwiseExp().getValues();
  }

  @Override
  public long[] getLargestValues(int n) {
    return logWeights.getLargestValues(n);
  }
}
