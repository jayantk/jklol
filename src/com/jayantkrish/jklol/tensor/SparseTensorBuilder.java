package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;

/**
 * Helper class for constructing {@link SparseTensor}s when the ordering of the
 * keys traversed is unknown. This will be less efficient than the constructor,
 * but often times more convenient.
 * 
 * @author jayantk
 */
public class SparseTensorBuilder extends AbstractTensorBase implements TensorBuilder {

  private SortedMap<Integer, Double> outcomes;

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
    this.outcomes = new TreeMap<Integer, Double>();
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
    if (outcomes.containsKey(index)) {
      return outcomes.get(index);
    }
    return 0.0;
  }
  
  @Override
  public int indexToKeyInt(int index) {
    return index;
  }
  
  @Override
  public int keyIntToIndex(int keyInt) {
    return keyInt;
  }

  /**
   * Returns {@code true} if this builder has a value associated with
   * {@code key}. Equivalent to {@code this.get(key) != 0.0}.
   * 
   * @param key
   * @return
   */
  public boolean containsKey(int[] key) {
    return outcomes.containsKey(dimKeyToKeyInt(key));
  }

  @Override
  public Iterator<int[]> keyIterator() {
    return new SparseKeyIterator(Lists.newArrayList(outcomes.keySet()), this);
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
    putByKeyInt(dimKeyToKeyInt(key), value);
  }
  
  public void putByKeyInt(int keyInt, double value) {
    if (value == 0.0) {
      outcomes.remove(keyInt);
    } else {
      outcomes.put(keyInt, value);
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
    for (int i = 0; i < maxKeyInt(); i++) {
      incrementEntryByKeyInt(amount, i);
    }
  }

  @Override
  public void incrementWithMultiplier(TensorBase other, double multiplier) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));

    Iterator<int[]> keyIterator = other.keyIterator();
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
  
  public void incrementEntryByKeyInt(double amount, int keyInt) {
    putByKeyInt(keyInt, getByIndex(keyInt) + amount);
  }

  @Override
  public void multiply(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));

    for (Integer keyInt : outcomes.keySet()) {
      putByKeyInt(keyInt, getByIndex(keyInt) * other.getByIndex(other.keyIntToIndex(keyInt)));
    }
  }

  @Override
  public void multiply(double amount) {
    if (amount == 0.0) {
      outcomes.clear();
      return;
    }

    for (int keyInt : outcomes.keySet()) {
      outcomes.put(keyInt, outcomes.get(keyInt) * amount);
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
    int[] tableKeyInts = new int[outcomes.size()];
    double[] tableValues = new double[outcomes.size()];
    int index = 0;
    for (Map.Entry<Integer, Double> entry : outcomes.entrySet()) {
      tableKeyInts[index] = entry.getKey();
      tableValues[index] = entry.getValue();
      index++;
    }
    return new SparseTensor(getDimensionNumbers(), getDimensionSizes(),
        tableKeyInts, tableValues);
  }
  
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("{");
    for (Map.Entry<Integer, Double> outcome : outcomes.entrySet()) {
      builder.append(Arrays.toString(keyIntToDimKey(outcome.getKey())));
      builder.append("=");
      builder.append(outcome.getValue());
      builder.append(", ");
    }
    builder.append("}");
    return builder.toString();
  }
}
