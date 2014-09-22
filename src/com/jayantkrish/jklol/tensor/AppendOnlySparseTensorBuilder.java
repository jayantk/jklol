package com.jayantkrish.jklol.tensor;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;

/**
 * A builder for {@code SparseTensor}s which only supports the {@code put}
 * method. This class only implements a subset of the {@code TensorBuilder}
 * interface, but is much more efficient when constructing large tensors.
 * 
 * @author jayantk
 */
public class AppendOnlySparseTensorBuilder extends AbstractTensorBase implements TensorBuilder {

  private static final long serialVersionUID = 1L;
  
  private List<Long> keys;
  private List<Double> values;

  public AppendOnlySparseTensorBuilder(int[] dimensionNums, int[] dimensionSizes) {
    super(dimensionNums, dimensionSizes);
    this.keys = Lists.newArrayList();
    this.values = Lists.newArrayList();
  }
  
  public static TensorFactory getFactory() {
    return new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new AppendOnlySparseTensorBuilder(dimNums, dimSizes);
      }
    };
  }

  @Override
  public int size() {
    return keys.size();
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
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<KeyValue> keyValuePrefixIterator(int[] keyPrefix) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getL2Norm() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double getTrace() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long[] getLargestValues(int n) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void put(int[] key, double value) {
    long keyNum = dimKeyToKeyNum(key);
    putByKeyNum(keyNum, value);
  }

  @Override
  public void putByKeyNum(long keyNum, double value) {
    keys.add(keyNum);
    values.add(value);
  }

  @Override
  public void increment(TensorBase other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void increment(double amount) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void incrementWithMultiplier(TensorBase other, double multiplier) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void incrementSquare(TensorBase other, double multiplier) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void incrementAdagrad(TensorBase other, TensorBase squareTensor, double multiplier) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void multiplyInverseAdagrad(TensorBase squareTensor, double constant, double multiplier) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void incrementSquareAdagrad(TensorBase gradient, TensorBase parameters, double multiplier) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void incrementOuterProductWithMultiplier(Tensor leftTensor, Tensor rightTensor,
      double multiplier) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void incrementEntry(double amount, int... key) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void incrementEntryByKeyNum(double amount, long keyNum) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void multiply(TensorBase other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void multiply(double amount) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void multiplyEntry(double amount, int... key) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void multiplyEntryByKeyNum(double amount, long keyNum) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void softThreshold(double threshold) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void findEntriesLargerThan(double threshold) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double innerProduct(TensorBase other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void exp() {
    throw new UnsupportedOperationException();
  }

  @Override
  public SparseTensor build() {
    long[] keyArray = Longs.toArray(keys);
    double[] valueArray = Doubles.toArray(values);
    return SparseTensor.fromUnorderedKeyValues(getDimensionNumbers(), getDimensionSizes(),
        keyArray, valueArray);
  }

  @Override
  public Tensor buildNoCopy() {
    return build();
  }

  @Override
  public TensorBuilder getCopy() {
    throw new UnsupportedOperationException();
  }
}
