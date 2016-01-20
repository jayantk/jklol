package com.jayantkrish.jklol.util;

import java.util.Arrays;

/**
 * Heap data structure for finding the K highest-scoring
 * elements of a collection of items.
 * 
 * @author jayantk
 *
 * @param <T>
 */
public class KbestHeap<T> {
  private double[] values;
  private T[] keys;
  private int size;
  private int maxElements;

  public KbestHeap(int maxElements, T[] keyType) {
    values = new double[maxElements + 1];
    keys = Arrays.copyOf(keyType, maxElements + 1);
    size = 0;
    this.maxElements = maxElements;
  }

  /**
   * Add {@code toQueue} to this with {@code score}.
   * Calling this method may remove the lowest-scoring
   * element if adding the new element causes the size of
   * {@code this} to exceed {@code this.maxElements}. 
   * 
   * @param toQueue
   * @param score
   * @return
   */
  public final int offer(T toQueue, double score) {
    HeapUtils.offer(keys, values, size, toQueue, score);
    size++;

    if (size > maxElements) {
      HeapUtils.removeMin(keys, values, size);
      size--;
    }
    return size;
  }

  public int size() {
    return size;
  }

  public T[] getKeys() {
    return keys;
  }

  public void clear() {
    this.size = 0;
  }
}
