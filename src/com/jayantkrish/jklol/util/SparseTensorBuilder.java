package com.jayantkrish.jklol.util;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;

/**
 * Helper class for constructing {@link SparseTensor}s when the ordering
 * of the keys traversed is unknown. This will be less efficient than the
 * constructor, but often times more convenient.
 * 
 * @author jayantk
 */
public class SparseTensorBuilder {
  private int[] dimensionNums;
  private SortedMap<int[], Double> outcomes;

  /**
   * Gets a builder which constructs a {@code SparseTensor} over
   * dimensions {@code dimensionNums}. {@code dimensionNums} must be in sorted
   * order, from smallest to largest.
   * 
   * @param dimensionNums
   */
  public SparseTensorBuilder(int[] dimensionNums) {
    Preconditions.checkArgument(Ordering.natural().isOrdered(Ints.asList(dimensionNums)));
    this.dimensionNums = dimensionNums;
    this.outcomes = new TreeMap<int[], Double>(Ints.lexicographicalComparator());
  }

  /**
   * Adds a key/value pair to the table. {@code key} contains the value index of
   * each dimension, in the same order as passed to the constructor. If
   * {@code key} is already in the table, this method overwrites its value. If
   * {@code value} is 0.0, {@code key} is deleted from this builder.
   * 
   * @param key
   * @param value
   */
  public void put(int[] key, double value) {
    Preconditions.checkArgument(key.length == dimensionNums.length);
    if (value == 0.0) {
      outcomes.remove(key);
    } else {
      outcomes.put(key, value);
    }
  }

  /**
   * Returns {@code true} if this builder has a value associated with
   * {@code key}.
   * 
   * @param key
   * @return
   */
  public boolean containsKey(int[] key) {
    return outcomes.containsKey(key);
  }

  /**
   * Gets the weight associated with {@code key} in {@code this}. If no weight
   * has been associated with {@code key}, returns 0.
   * 
   * @param key
   * @return
   */
  public double get(int[] key) {
    if (outcomes.containsKey(key)) {
      return outcomes.get(key);
    }
    return 0.0;
  }
  
  /**
   * Gets the number of keys in {@code this} with nonzero weight.
   * 
   * @return
   */
  public int size() {
    return outcomes.size();
  }

  /**
   * Gets an iterator over all keys of {@code this} with nonzero probability.
   * 
   * @return
   */
  public Iterator<int[]> keyIterator() {
    return outcomes.keySet().iterator();
  }

  /**
   * Constructs and returns a {@code SparseTensor} containing all of the
   * key/value pairs added to {@code this}.
   * 
   * @return
   */
  public SparseTensor build() {
    int[][] tableOutcomes = new int[dimensionNums.length][outcomes.size()];
    double[] tableValues = new double[outcomes.size()];
    int index = 0;
    for (Map.Entry<int[], Double> entry : outcomes.entrySet()) {
      for (int j = 0; j < dimensionNums.length; j++) {
        tableOutcomes[j][index] = entry.getKey()[j];
      }
      tableValues[index] = entry.getValue();
      index++;
    }
    return new SparseTensor(dimensionNums, tableOutcomes, tableValues);
  }
}
