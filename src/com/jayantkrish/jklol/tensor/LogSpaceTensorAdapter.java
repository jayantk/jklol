package com.jayantkrish.jklol.tensor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Preconditions;

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
  
  private final DenseTensor logWeights;

  public LogSpaceTensorAdapter(DenseTensor logWeights) {
    super(logWeights.getDimensionNumbers(), logWeights.getDimensionSizes());
    this.logWeights = logWeights;
  }

  @Override
  public int size() {
    return logWeights.size();
  }

  @Override
  public double getByIndex(int index) {
    return Math.exp(logWeights.getByIndex(index));
  }

  @Override
  public double getLogByIndex(int index) {
    return logWeights.getByIndex(index);
  }

  @Override
  public int keyNumToIndex(long keyNum) {
    return logWeights.keyNumToIndex(keyNum);
  }

  @Override
  public long indexToKeyNum(int index) {
    return logWeights.indexToKeyNum(index);
  }

  @Override
  public Iterator<KeyValue> keyValueIterator() {
    return new LogSpaceKeyValueIterator(logWeights.keyValueIterator());
  }

  @Override
  public Iterator<KeyValue> keyValuePrefixIterator(int[] keyPrefix) {
    return new LogSpaceKeyValueIterator(logWeights.keyValuePrefixIterator(keyPrefix));
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
  public Tensor elementwiseProduct(Tensor other) {
    return new LogSpaceTensorAdapter(logWeights.elementwiseAddition(other.elementwiseLog()));
  }
  
  @Override
  public Tensor innerProduct(Tensor other) {
    DenseTensor product = logWeights.elementwiseAddition(other.elementwiseLog());
    return (new LogSpaceTensorAdapter(product)).sumOutDimensions(other.getDimensionNumbers());
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
  public Tensor elementwiseExp() {
    return new LogSpaceTensorAdapter(logWeights.elementwiseExp());
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
    // This method is unsupported because it's behavior will be bizarre:
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
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public long[] getLargestValues(int n) {
    return logWeights.getLargestValues(n);
  }

  private static final class LogSpaceKeyValueIterator implements Iterator<KeyValue> {
    private final Iterator<KeyValue> logIterator;

    public LogSpaceKeyValueIterator(Iterator<KeyValue> logIterator) {
      this.logIterator = Preconditions.checkNotNull(logIterator);
    }

    @Override
    public boolean hasNext() {
      return logIterator.hasNext();
    }

    @Override
    public KeyValue next() {
      KeyValue next = logIterator.next();
      next.setValue(Math.exp(next.getValue()));
      return next;
    }

    @Override
    public void remove() {
      logIterator.remove();
    }
  }
}
