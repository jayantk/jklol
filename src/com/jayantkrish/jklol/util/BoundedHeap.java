package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.List;

public class BoundedHeap<T> {
  
  private final T[] items;
  private final double[] weights;
  
  private int currentSize;
  private int maxSize;

  public BoundedHeap(int maxSize, T[] array) {
    this.items = Arrays.copyOf(array, maxSize + 1);
    Arrays.fill(items, null);
    this.weights = new double[maxSize + 1];

    this.maxSize = maxSize;
    this.currentSize = 0;
  }
  
  public void offer(T item, double weight) {
    HeapUtils.offer(items, weights, currentSize, item, weight);
    currentSize++;
    if (currentSize > maxSize) {
      HeapUtils.removeMin(items, weights, currentSize);
      currentSize--;
    }
  }
  
  public List<T> getItems() {
    return Arrays.asList(Arrays.copyOfRange(items, 0, currentSize));
  }
  
  public int getSize() {
    return currentSize;
  }
}
