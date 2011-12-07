package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

/**
 * Helper class for constructing {@link SparseTensor}s when the ordering of the
 * keys traversed is unknown. This will be less efficient than the constructor,
 * but often times more convenient.
 * 
 * @author jayantk
 */
public class SparseTensorBuilder extends AbstractTensorBase implements TensorBuilder {

  private SortedMap<int[], Double> outcomes;

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
    this.outcomes = new TreeMap<int[], Double>(Ints.lexicographicalComparator());
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
  public double get(int... key) {
    Preconditions.checkArgument(key.length == getDimensionNumbers().length);
    int[] dimSizes = getDimensionSizes();
    for (int i = 0; i < key.length; i++) {
      Preconditions.checkArgument(key[i] >= 0 && key[i] < dimSizes[i]);
    }

    if (outcomes.containsKey(key)) {
      return outcomes.get(key);
    }
    return 0.0;
  }

  /**
   * Returns {@code true} if this builder has a value associated with
   * {@code key}. Equivalent to {@code this.get(key) != 0.0}.
   * 
   * @param key
   * @return
   */
  public boolean containsKey(int[] key) {
    return outcomes.containsKey(key);
  }

  @Override
  public Iterator<int[]> keyIterator() {
    return outcomes.keySet().iterator();
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
    Preconditions.checkArgument(key.length == getDimensionNumbers().length);
    if (value == 0.0) {
      outcomes.remove(key);
    } else {
      outcomes.put(Arrays.copyOf(key, key.length), value);
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
    Iterator<int[]> allKeyIterator = new IntegerArrayIterator(getDimensionSizes());
    while (allKeyIterator.hasNext()) {
      incrementEntry(amount, allKeyIterator.next());
    }
  }

  @Override
  public void incrementWithMultiplier(TensorBase other, double multiplier) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));

    Iterator<int[]> otherKeys = other.keyIterator();
    while (otherKeys.hasNext()) {
      int[] key = otherKeys.next();
      incrementEntry(other.get(key) * multiplier, key);
    }
  }

  @Override
  public void incrementEntry(double amount, int... key) {
    Preconditions.checkArgument(key.length == getDimensionNumbers().length);

    double value = 0.0;
    if (outcomes.containsKey(key)) {
      value = outcomes.get(key);
    }
    put(key, value + amount);
  }

  @Override
  public void multiply(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));

    Iterator<int[]> keyIterator = keyIterator();
    while (keyIterator.hasNext()) {
      int[] key = keyIterator.next();
      put(key, get(key) * other.get(key));
    }
  }

  @Override
  public void multiply(double amount) {
    if (amount == 0.0) {
      outcomes.clear();
      return;
    }

    for (int[] key : outcomes.keySet()) {
      outcomes.put(key, outcomes.get(key) * amount);
    }
  }
  
  @Override
  public void multiplyEntry(double amount, int... key) {
    put(key, get(key) * amount);
  }

  /**
   * Constructs and returns a {@code SparseTensor} containing all of the
   * key/value pairs added to {@code this}.
   * 
   * @return
   */
  @Override
  public SparseTensor build() {
    int[][] tableOutcomes = new int[getDimensionNumbers().length][outcomes.size()];
    double[] tableValues = new double[outcomes.size()];
    int index = 0;
    for (Map.Entry<int[], Double> entry : outcomes.entrySet()) {
      for (int j = 0; j < getDimensionNumbers().length; j++) {
        tableOutcomes[j][index] = entry.getKey()[j];
      }
      tableValues[index] = entry.getValue();
      index++;
    }
    return new SparseTensor(getDimensionNumbers(), getDimensionSizes(),
        tableOutcomes, tableValues);
  }
  
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("{");
    for (Map.Entry<int[], Double> outcome : outcomes.entrySet()) {
      builder.append(Arrays.toString(outcome.getKey()));
      builder.append("=");
      builder.append(outcome.getValue());
      builder.append(", ");
    }
    builder.append("}");
    return builder.toString();
  }
}
