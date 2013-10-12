package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

/**
 * Builder for incrementally constructing sparse tensors. 
 *
 * <p> This implementation is quite inefficient, and should not be used in
 * performance-sensitive code.
 * 
 * @author jayantk
 */
public class SparseTensorBuilder extends AbstractTensorBase implements TensorBuilder {

  private static final long serialVersionUID = 6115742485230128674L;

  private SortedMap<Long, Double> outcomes;

  // The tensor interface forces us to assign each value in {@code this} a
  // unique integer index.
  private int nextIndex;
  private BiMap<Long, Integer> outcomeIndexes;

  /**
   * Gets a builder which constructs a {@code SparseTensor} over dimensions
   * {@code dimensionNums}. {@code dimensionNums} must be in sorted order, from
   * smallest to largest.
   * 
   * @param dimensionNums
   */
  public SparseTensorBuilder(int[] dimensionNums, int[] dimensionSizes) {
    super(dimensionNums, dimensionSizes);
    Preconditions.checkArgument(Ordering.natural().isOrdered(Ints.asList(dimensionNums)));
    this.outcomes = new TreeMap<Long, Double>();
    this.nextIndex = 0;
    this.outcomeIndexes = HashBiMap.create();
  }

  /**
   * Copy constructor.
   */
  private SparseTensorBuilder(SparseTensorBuilder builder) {
    super(builder.getDimensionNumbers(), builder.getDimensionSizes());
    this.outcomes = Maps.newTreeMap(builder.outcomes);
    this.nextIndex = builder.nextIndex;
    this.outcomeIndexes = HashBiMap.create(builder.outcomeIndexes);
  }
  
  /**
   * Gets a builder which contains the same key value pairs as {@code tensor}. 
   * @param tensor
   * @return
   */
  public static SparseTensorBuilder copyOf(TensorBase tensor) {
    SparseTensorBuilder builder = new SparseTensorBuilder(tensor.getDimensionNumbers(), 
        tensor.getDimensionSizes());
    Iterator<KeyValue> initialWeightIter = tensor.keyValueIterator();
    while (initialWeightIter.hasNext()) {
      KeyValue keyValue = initialWeightIter.next();
      builder.put(keyValue.getKey(), keyValue.getValue());
    }
    return builder;
  }

  /**
   * Gets a {@code TensorFactory} which creates {@code SparseTensorBuilder}s.
   * 
   * @return
   */
  public static TensorFactory getFactory() {
    return new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new SparseTensorBuilder(dimNums, dimSizes);
      }
    };
  }

  // /////////////////////////////////////////////////////////////
  // TensorBase methods
  // /////////////////////////////////////////////////////////////

  /**
   * Gets the number of keys in {@code this} with nonzero weight.
   * 
   * @return
   */
  @Override
  public int size() {
    return outcomes.size();
  }

  @Override
  public double getByIndex(int index) {
    if (index == -1) { return 0.0; }
    
    long keyNum = indexToKeyNum(index);
    if (outcomes.containsKey(keyNum)) {
      return outcomes.get(keyNum);
    }
    return 0.0;
  }
  
  @Override
  public double getLogByIndex(int index) {
    return Math.log(getByIndex(index));
  }

  @Override
  public long indexToKeyNum(int index) {
    return outcomeIndexes.inverse().get(index);
  }

  @Override
  public int keyNumToIndex(long keyNum) {
    if (outcomeIndexes.containsKey(keyNum)) {
      return outcomeIndexes.get(keyNum);
    }
    // -1 is reserved for all keys which are not in this tensor. 
    return -1;
  }

  /**
   * Returns {@code true} if this builder has a value associated with
   * {@code key}. Equivalent to {@code this.get(key) != 0.0}.
   * 
   * @param key
   * @return
   */
  public boolean containsKey(int[] key) {
    return outcomes.containsKey(dimKeyToKeyNum(key));
  }

  @Override
  public Iterator<KeyValue> keyValueIterator() {
    return new SparseKeyValueIterator(Longs.toArray(outcomes.keySet()),
        Doubles.toArray(outcomes.values()), 0, outcomes.size(), this);
  }
  
  @Override
  public Iterator<KeyValue> keyValuePrefixIterator(int[] keyPrefix) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public double getL2Norm() {
    double sumSquared = 0.0;
    for (Double value : outcomes.values()) {
      sumSquared += value * value;
    }
    return Math.sqrt(sumSquared);
  }
  
  @Override
  public double getTrace() {
    double sum = 0.0;
    for (double value : outcomes.values()) {
      sum += value;
    }
    return sum;
  }
  
  @Override
  public double innerProduct(TensorBase other) {
    TensorBuilder product = getCopy();
    product.multiply(other);
    return product.getTrace();
  }
  
  @Override
  public long[] getLargestValues(int n) {
    return build().getLargestValues(n);
  }

  // /////////////////////////////////////////////////////////////
  // TensorBuilder methods
  // /////////////////////////////////////////////////////////////

  @Override
  public void put(int[] key, double value) {
    putByKeyNum(dimKeyToKeyNum(key), value);
  }

  public void putByKeyNum(long keyNum, double value) {
    if (value == 0.0) {
      outcomes.remove(keyNum);
    } else {
      outcomes.put(keyNum, value);
      if (!outcomeIndexes.containsKey(keyNum)) {
        outcomeIndexes.put(keyNum, nextIndex);
        nextIndex++;
      }
    }
  }

  @Override
  public void increment(TensorBase other) {
    incrementWithMultiplier(other, 1.0);
  }

  @Override
  public void increment(double amount) {
    // Invoking this method on a sparse tensor is a bad idea, because it
    // destroys the sparsity. Use a dense tensor instead.
    for (long keyNum = 0; keyNum < getMaxKeyNum(); keyNum++) {
      incrementEntryByKeyNum(amount, keyNum);
    }
  }

  @Override
  public void incrementWithMultiplier(TensorBase other, double multiplier) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));

    Iterator<KeyValue> keyValueIterator = other.keyValueIterator();
    while (keyValueIterator.hasNext()) {
      KeyValue keyValue = keyValueIterator.next();
      incrementEntry(keyValue.getValue() * multiplier, keyValue.getKey());
    }
  }
  
  @Override
  public void incrementOuterProductWithMultiplier(Tensor leftTensor, Tensor rightTensor,
      double multiplier) {
    incrementWithMultiplier(leftTensor.outerProduct(rightTensor), multiplier);
  }

  @Override
  public void incrementEntry(double amount, int... key) {
    Preconditions.checkArgument(key.length == getDimensionNumbers().length);
    put(key, getByDimKey(key) + amount);
  }

  @Override
  public void incrementEntryByKeyNum(double amount, long keyNum) {
    putByKeyNum(keyNum, get(keyNum) + amount);
  }

  @Override
  public void multiply(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    
    for (Long keyNum : outcomes.keySet()) {
      putByKeyNum(keyNum, get(keyNum) * other.getByIndex(other.keyNumToIndex(keyNum)));
    }
  }

  @Override
  public void multiply(double amount) {
    if (amount == 0.0) {
      outcomes.clear();
      return;
    }

    for (long keyNum : outcomes.keySet()) {
      outcomes.put(keyNum, outcomes.get(keyNum) * amount);
    }
  }

  @Override
  public void multiplyEntry(double amount, int... key) {
    put(key, getByDimKey(key) * amount);
  }

  @Override
  public void multiplyEntryByKeyNum(double amount, long keyNum) {
    putByKeyNum(keyNum, get(keyNum) * amount);
  }

  @Override
  public void softThreshold(double threshold) {
    double negativeThreshold = -1.0 * threshold;
    for (long keyNum : outcomes.keySet()) {
      double value = outcomes.get(keyNum);
      if (value > threshold) {
        value -= threshold;
      } else if (value < negativeThreshold) {
        value += threshold;
      } else {
        value = 0.0;
      }
      outcomes.put(keyNum, value);
    }
  }
  
  @Override
  public void exp() {
    for (long keyNum = 0; keyNum < getMaxKeyNum(); keyNum++) {
      putByKeyNum(keyNum, Math.exp(get(keyNum)));
    }
  }

  /**
   * Constructs and returns a {@code SparseTensor} containing all of the
   * key/value pairs added to {@code this}.
   * 
   * @return
   */
  @Override
  public SparseTensor build() {
    long[] tableKeyNums = new long[outcomes.size()];
    double[] tableValues = new double[outcomes.size()];
    int index = 0;
    for (Map.Entry<Long, Double> entry : outcomes.entrySet()) {
      tableKeyNums[index] = entry.getKey();
      tableValues[index] = entry.getValue();
      index++;
    } 
    return new SparseTensor(getDimensionNumbers(), getDimensionSizes(),
        tableKeyNums, tableValues);
  }
  
  @Override
  public SparseTensor buildNoCopy() {
    return build();
  }

  @Override
  public SparseTensorBuilder getCopy() {
    return new SparseTensorBuilder(this);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("{");
    for (Map.Entry<Long, Double> outcome : outcomes.entrySet()) {
      builder.append(Arrays.toString(keyNumToDimKey(outcome.getKey())));
      builder.append("=");
      builder.append(outcome.getValue());
      builder.append(", ");
    }
    builder.append("}");
    return builder.toString();
  }
}
