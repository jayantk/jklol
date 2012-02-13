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
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

/**
 * Helper class for constructing {@link SparseTensor}s when the ordering of the
 * keys traversed is unknown. This will be less efficient than the constructor,
 * but often times more convenient.
 * 
 * @author jayantk
 */
public class SparseTensorBuilder extends AbstractTensorBase implements TensorBuilder {

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
  public SparseTensorBuilder(SparseTensorBuilder builder) {
    super(builder.getDimensionNumbers(), builder.getDimensionSizes());
    this.outcomes = Maps.newTreeMap(builder.outcomes);
    this.nextIndex = builder.nextIndex;
    this.outcomeIndexes = HashBiMap.create(builder.outcomeIndexes);
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
  public Iterator<int[]> keyValueIterator() {
    return new SparseKeyIterator(Longs.toArray(outcomes.keySet()), 
        0, outcomes.size(), this);
  }
  
  @Override
  public Iterator<int[]> keyPrefixIterator(int[] keyPrefix) {
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
      outcomeIndexes.remove(keyNum);
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
    for (int i = 0; i < maxKeyNum(); i++) {
      incrementEntryByKeyInt(amount, i);
    }
  }

  @Override
  public void incrementWithMultiplier(TensorBase other, double multiplier) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));

    Iterator<int[]> keyIterator = other.keyValueIterator();
    while (keyIterator.hasNext()) {
      int[] key = keyIterator.next();
      incrementEntry(other.getByDimKey(key) * multiplier, key);
    }
  }

  @Override
  public void incrementEntry(double amount, int... key) {
    Preconditions.checkArgument(key.length == getDimensionNumbers().length);
    put(key, getByDimKey(key) + amount);
  }

  public void incrementEntryByKeyInt(double amount, int keyNum) {
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
