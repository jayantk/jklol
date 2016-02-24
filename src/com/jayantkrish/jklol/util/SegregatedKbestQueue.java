package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class SegregatedKbestQueue<T> implements SearchQueue<T> {
  
  private final List<KbestQueue<T>> queues;
  private final Function<T, Integer> hash;
  private final T[] items;
  
  public SegregatedKbestQueue(int numQueues, int maxQueueSize,
      Function<T, Integer> hash, T[] keyType) {
    queues = Lists.newArrayList();
    for (int i = 0; i < numQueues; i++) {
      queues.add(new KbestQueue<T>(maxQueueSize, keyType));
    }
    this.hash = hash;
    this.items = Arrays.copyOf(keyType, numQueues * maxQueueSize);
  }

  @Override
  public void offer(T item, double score) {
    int index = hash.apply(item);
    if (index >= 0) {
      queues.get(index).offer(item, score);
    }
  }

  @Override
  public int size() {
    int size = 0;
    for (int i = 0; i < queues.size(); i++) {
      size += queues.get(i).size();
    }
    return size;
  }

  @Override
  public T[] getItems() {
    int ind = 0;
    for (int i = 0; i < queues.size(); i++) {
      int queueSize = queues.get(i).size();
      T[] queueItems = queues.get(i).getItems();
      for (int j = 0; j < queueSize; j++) {
        items[ind] = queueItems[j];
        ind++;
      }
    }
    return items;
  }

  @Override
  public void clear() {
    for (int i = 0; i < queues.size(); i++) {
      queues.get(i).clear();
    }
  }
}
