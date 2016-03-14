package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * Heap data structure for finding the K highest-scoring
 * elements of a collection of items.
 * 
 * @author jayantk
 *
 * @param <T>
 */
public class KbestQueue<T> implements SearchQueue<T> {
  private double[] values;
  private T[] keys;
  private int size;
  private int maxElements;

  public KbestQueue(int maxElements, T[] keyType) {
    values = new double[maxElements + 1];
    keys = Arrays.copyOf(keyType, maxElements + 1);
    size = 0;
    this.maxElements = maxElements;
  }
  
  public static <T> KbestQueue<T> create(int maxElements, T[] keyType) {
    return new KbestQueue<T>(maxElements, keyType);
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
  public final void offer(T toQueue, double score) {
    HeapUtils.offer(keys, values, size, toQueue, score);
    size++;

    if (size > maxElements) {
      HeapUtils.removeMin(keys, values, size);
      size--;
    }
  }
  
  public T removeMin() {
    T min = keys[0];
    HeapUtils.removeMin(keys, values, size);
    size--;
    return min;
  }

  public int size() {
    return size;
  }

  public T[] getItems() {
    return keys;
  }
  
  /**
   * Pops all of the items in this queue and places them
   * into a list sorted in descending score order (i.e.,
   * with the highest scoring item first). This queue
   * will be empty after calling this method.
   *  
   * @param queue
   * @return
   */
  public List<T> toSortedList() {
    List<T> sortedItems = Lists.newArrayList();
    while (size() > 0) {
      sortedItems.add(removeMin());
    }
    Collections.reverse(sortedItems);
    return sortedItems;
  }

  public void clear() {
    this.size = 0;
  }
}
