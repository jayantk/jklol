package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

/**
 * An efficiently encoded mapping between pairs of integers. This
 * class does not support removal.
 * <p>
 * The expected access pattern for this kind of Multimap is long
 * sequences of insertions, followed by long sequences of lookups.
 * This class will perform poorly if insertions and lookups are
 * frequently interleaved.
 * 
 * @author jayantk
 */
public class IntMultimap implements Multimap<Integer, Integer> {

  private int[] sortedKeys;
  private int[] sortedValues;

  private int[] keySet;
  
  private int numUnsortedItems;
  private int[] unsortedKeys;
  private int[] unsortedValues;

  private static final int INITIAL_UNSORTED_BUFFER_SIZE = 1000;

  /**
   * Expects keys to be sorted.
   * 
   * @param keys
   * @param values
   */
  private IntMultimap(int[] keys, int[] values) {
    this.sortedKeys = Preconditions.checkNotNull(keys);
    this.sortedValues = Preconditions.checkNotNull(values);
    
    this.keySet = null;

    this.numUnsortedItems = 0;
    this.unsortedKeys = new int[INITIAL_UNSORTED_BUFFER_SIZE];
    this.unsortedValues = new int[INITIAL_UNSORTED_BUFFER_SIZE];
    
    rebuildKeySet();
  }
  
  private IntMultimap(int[] keys, int[] values, int unsortedCapacity) {
    this.sortedKeys = Preconditions.checkNotNull(keys);
    this.sortedValues = Preconditions.checkNotNull(values);
    
    this.keySet = null;

    this.numUnsortedItems = 0;
    this.unsortedKeys = new int[unsortedCapacity];
    this.unsortedValues = new int[unsortedCapacity];
    
    rebuildKeySet();
  }


  /**
   * Creates and returns an empty multimap.
   * 
   * @return
   */
  public static IntMultimap create() {
    return new IntMultimap(new int[0], new int[0]);
  }
  
  /**
   * This method does not copy {@code keys} or {@code values}.
   *  
   * @param keys
   * @param values
   * @param unsortedCapacity
   * @return
   */
  public static IntMultimap createFromUnsortedArrays(int[] keys, int[] values, int unsortedCapacity) {
    Preconditions.checkArgument(keys.length == values.length);
    ArrayUtils.sortKeyValuePairs(keys, values, 0, keys.length);
    return new IntMultimap(keys, values, unsortedCapacity);
  }

  /**
   * Creates a new multimap that reverses the keys and values in {@code map}.
   * 
   * @param map
   * @return
   */
  public static IntMultimap invertFrom(Multimap<? extends Integer, ? extends Integer> map) {
    if (map instanceof IntMultimap) {
      IntMultimap other = (IntMultimap) map;
      // This is unnecessary, but it makes this method easier to implement.
      other.reindexItems();

      int[] newSortedKeys = Arrays.copyOf(other.sortedValues, other.sortedValues.length);
      int[] newSortedValues = Arrays.copyOf(other.sortedKeys, other.sortedKeys.length);
     
      ArrayUtils.sortKeyValuePairs(newSortedKeys, newSortedValues, 0, newSortedKeys.length);

      return new IntMultimap(newSortedKeys, newSortedValues);
    } else {
      IntMultimap inverse = IntMultimap.create();
      Multimaps.invertFrom(map, inverse);
      return inverse;
    }
  }

  @Override
  public Map<Integer, Collection<Integer>> asMap() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    sortedKeys = new int[0];
    sortedValues = new int[0];

    numUnsortedItems = 0;
  }

  /**
   * Moves all elements from the unsorted portion of this map into the
   * sorted portion.
   */
  public void reindexItems() {
    if (numUnsortedItems > 0) {
      int[] newSortedKeys = Arrays.copyOf(sortedKeys, sortedKeys.length + numUnsortedItems);
      int[] newSortedValues = Arrays.copyOf(sortedValues, sortedValues.length + numUnsortedItems);

      int oldLength = sortedKeys.length;
      for (int i = 0; i < numUnsortedItems; i++) {
        newSortedKeys[i + oldLength] = unsortedKeys[i];
        newSortedValues[i + oldLength] = unsortedValues[i];
      }

      ArrayUtils.sortKeyValuePairs(newSortedKeys, newSortedValues, 0, newSortedKeys.length);

      sortedKeys = newSortedKeys;
      sortedValues = newSortedValues;
      numUnsortedItems = 0;
      
      rebuildKeySet();
    }
  }

  /**
   * Doubles the size of the unsorted portion of the map.
   * 
   * @return
   */
  private void resizeMap() {
    int[] newUnsortedKeys = Arrays.copyOf(unsortedKeys, unsortedKeys.length * 2);
    int[] newUnsortedValues = Arrays.copyOf(unsortedValues, unsortedValues.length * 2);

    unsortedKeys = newUnsortedKeys;
    unsortedValues = newUnsortedValues;
  }

  private void rebuildKeySet() {
    int curKey = -1;
    int numUniqueKeys = 0;
    for (int i = 0; i < sortedKeys.length; i++) {
      if (i == 0 || sortedKeys[i] != curKey) {
        numUniqueKeys++;
        curKey = sortedKeys[i];
      }
    }

    int[] keySetArray = new int[numUniqueKeys];
    int curKeyIndex = 0;
    for (int i = 0; i < sortedKeys.length; i++) {
      if (i == 0 || sortedKeys[i] != curKey) {
        keySetArray[curKeyIndex] = sortedKeys[i];
        curKeyIndex++;
        curKey = sortedKeys[i];
      }
    }

    Preconditions.checkState(curKeyIndex == numUniqueKeys);
    keySet = keySetArray;
  }

  /**
   * Finds the first index in {@code sortedKeys} containing
   * {@code key}.
   * 
   * @param key
   * @return
   */
  private int getKeyIndex(int key) {
    reindexItems();

    int index = Arrays.binarySearch(sortedKeys, key);
    if (index >= 0) {
      int prevIndex = index - 1;
      while (prevIndex >= 0 && sortedKeys[prevIndex] == key) {
        index = prevIndex;
        prevIndex = index - 1;
      }
      return index;
    } else {
      return -1;
    }
  }

  @Override
  public boolean containsEntry(Object keyObj, Object valObj) {
    if (keyObj instanceof Integer && valObj instanceof Integer) {
      int key = (Integer) keyObj;
      int value = (Integer) valObj;

      int index = getKeyIndex(key);
      if (index < 0) {
        // Key wasn't found.
        return false;
      }

      while (index < sortedKeys.length && sortedKeys[index] == key) {
        if (sortedValues[index] == value) {
          return true;
        }
        index++;
      }
    }
    return false;
  }

  @Override
  public boolean containsKey(Object keyObj) {
    if (keyObj instanceof Integer) {
      return getKeyIndex((Integer) keyObj) >= 0;
    }
    return false;
  }

  public final boolean containsKey(int intKey) {
    return getKeyIndex(intKey) >= 0;
  }

  /**
   * This method is inefficient -- it takes linear time in the size of
   * the map.
   */
  @Override
  public boolean containsValue(Object valObj) {
    reindexItems();

    if (valObj instanceof Integer) {
      int val = (Integer) valObj;
      for (int i = 0; i < sortedValues.length; i++) {
        if (sortedValues[i] == val) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public Collection<Entry<Integer, Integer>> entries() {
    throw new UnsupportedOperationException();
    /*
     * List<Entry<Integer, Integer>> entries =
     * Lists.newArrayListWithCapacity(sortedKeys.length +
     * numUnsortedItems); for (int i = 0; i < sortedKeys.length; i++)
     * { entries.add(new Entry<Integer, Integer>()) }
     */
  }

  @Override
  public Collection<Integer> get(Integer key) {
    int index = getKeyIndex(key);
    if (index < 0) {
      // Key wasn't found.
      return Collections.emptyList();
    }

    List<Integer> values = Lists.newArrayList();
    while (index < sortedKeys.length && sortedKeys[index] == key) {
      values.add(sortedValues[index]);
      index++;
    }
    return values;
  }

  public final int[] getArray(int key) {
    int firstIndex = getKeyIndex(key);
    if (firstIndex < 0) {
      return new int[0];
    }
    
    int lastIndex = firstIndex;
    while (lastIndex < sortedKeys.length && sortedKeys[lastIndex] == key) {
      lastIndex++;
    }
    return Arrays.copyOfRange(sortedValues, firstIndex, lastIndex);
  }

  @Override
  public boolean isEmpty() {
    return numUnsortedItems == 0 && sortedKeys.length == 0;
  }
  
  @Override
  public Set<Integer> keySet() {
    reindexItems();
    return Sets.newHashSet(Ints.asList(sortedKeys));
  }
  
  public final int[] keySetArray() {
    reindexItems();
    return keySet;
  }

  @Override
  public Multiset<Integer> keys() {
    reindexItems();
    return HashMultiset.create(Ints.asList(sortedKeys));
  }

  @Override
  public boolean put(Integer key, Integer value) {
    if (numUnsortedItems == unsortedKeys.length) {
      resizeMap();
    }

    unsortedKeys[numUnsortedItems] = key;
    unsortedValues[numUnsortedItems] = value;
    numUnsortedItems++;
    return true;
  }

  @Override
  public boolean putAll(Multimap<? extends Integer, ? extends Integer> map) {
    for (Entry<? extends Integer, ? extends Integer> entry : map.entries()) {
      put(entry.getKey(), entry.getValue());
    }
    return map.size() > 0;
  }

  @Override
  public boolean putAll(Integer key, Iterable<? extends Integer> values) {
    boolean mapChanged = false;
    for (Integer value : values) {
      put(key, value);
      mapChanged = true;
    }
    return mapChanged;
  }

  @Override
  public boolean remove(Object key, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<Integer> removeAll(Object arg0) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<Integer> replaceValues(Integer arg0, Iterable<? extends Integer> arg1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return sortedKeys.length + numUnsortedItems;
  }

  @Override
  public Collection<Integer> values() {
    // Technically, we could do this without reindexing by
    // concatenating
    // the arrays. But I'm never going to use this method anyway.
    reindexItems();
    return Ints.asList(sortedValues);
  }
}
