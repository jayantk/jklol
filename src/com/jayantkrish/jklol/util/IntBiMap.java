package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

/**
 * A {@code BiMap} between two sets of integers.
 * 
 * @author jayant
 *
 */
public class IntBiMap implements BiMap<Integer, Integer> {

  private final int[] keys;
  private final int[] values;
  
  private IntBiMap(int[] keys, int[] values) {
    Preconditions.checkArgument(keys.length == values.length);
    this.keys = keys;
    this.values = values;
  }
  
  public static final IntBiMap fromSortedKeyValues(int[] keys, int[] values) {
    return new IntBiMap(keys, values);
  }
  
  public static final IntBiMap fromUnsortedKeyValues(int[] keys, int[] values) {
    ArrayUtils.sortKeyValuePairs(keys, values, 0, keys.length);
    return new IntBiMap(keys, values);
  }

  @Override
  public int size() {
    return keys.length;
  }

  @Override
  public boolean isEmpty() {
    return keys.length == 0;
  }
  
  private final int getKeyIndex(int key) {
    return Arrays.binarySearch(keys, key);
  }
  
  private final int getValueIndex(int value) {
    return Ints.indexOf(values, value);
  }

  @Override
  public boolean containsKey(Object key) {
    if (key instanceof Integer) {
      return containsKey((int) ((Integer) key));
    }
    return false;
  }

  public boolean containsKey(int key) {
    return getKeyIndex(key) >= 0;
  }

  @Override
  public boolean containsValue(Object value) {
    if (value instanceof Integer) {
      return containsValue((Integer) value);
    }
    return false;
  }

  public boolean containsValue(int value) {
    return getValueIndex(value) >= 0;
  }

  @Override
  public Integer get(Object key) {
    if (key instanceof Integer) {
      int index = getKeyIndex((Integer) key);
      if (index >= 0) {
        return values[index];
      }
    }
    return null;
  }

  /**
   * If this map does not contain {@code key}, {@code defaultValue} is returned.
   * 
   * @param key
   * @return
   */
  public int get(int key, int defaultValue) {
    int index = getKeyIndex(key);
    if (index >= 0) {
      return values[index];
    } else {
      return defaultValue;
    }
  }

  @Override
  public Integer remove(Object key) {
    throw new UnsupportedOperationException("IntBiMap does not support mutation operations");
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException("IntBiMap does not support mutation operations");
  }

  @Override
  public Set<Integer> keySet() {
    return Sets.newHashSet(Ints.asList(keys));
  }

  @Override
  public Set<java.util.Map.Entry<Integer, Integer>> entrySet() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Integer forcePut(Integer arg0, Integer arg1) {
    throw new UnsupportedOperationException("IntBiMap does not support mutation operations");
  }

  @Override
  public IntBiMap inverse() {
    return IntBiMap.fromUnsortedKeyValues(Arrays.copyOf(values, values.length),
        Arrays.copyOf(keys, keys.length));
  }

  @Override
  public Integer put(Integer arg0, Integer arg1) {
    throw new UnsupportedOperationException("IntBiMap does not support mutation operations");
  }

  @Override
  public void putAll(Map<? extends Integer, ? extends Integer> arg0) {
    throw new UnsupportedOperationException("IntBiMap does not support mutation operations");
  }

  @Override
  public Set<Integer> values() {
    return Sets.newHashSet(Ints.asList(values));
  }
  
  /**
   * Returns a new {@code IntBiMap} which contains all of the 
   * key/value pairs in {@code this} and {@code other}.
   *  
   * @param other
   * @return
   */
  public IntBiMap union(IntBiMap other) {
    int newLength = keys.length + other.keys.length;

    int[] newKeys = Arrays.copyOf(keys, newLength);
    ArrayUtils.copy(other.keys, 0, newKeys, keys.length, other.keys.length);
    int[] newValues = Arrays.copyOf(values, newLength);
    ArrayUtils.copy(other.values, 0, newValues, values.length, other.values.length);

    return IntBiMap.fromUnsortedKeyValues(newKeys, newValues);
  }
}
