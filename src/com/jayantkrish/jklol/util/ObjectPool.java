package com.jayantkrish.jklol.util;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

public class ObjectPool<T> {

  private final T[] free;
  private int numFree;
  private int numAlloc;
  private final int maxSize;

  private Supplier<T> allocator;
  
  public ObjectPool(Supplier<T> allocator, int maxSize, T[] typeArr) {
    this.allocator = Preconditions.checkNotNull(allocator);
    this.free = Arrays.copyOf(typeArr, maxSize);
    this.numFree = 0;
    this.numAlloc = 0;
    this.maxSize = maxSize;
  }

  /*
  public int numFree() {
    return numFree;
  }
  
  public int numAlloc() {
    return free.length - numFree;
  }
  */

  public T alloc() {
    if (numFree == 0) {
      Preconditions.checkState(numAlloc < maxSize);
      numAlloc++;
      return allocator.get();
    } else {
      T item = free[numFree - 1];
      numFree--;
      return item;
    }
  }

  public void dealloc(T item) {
    free[numFree] = item;
    numFree++;
  }
  
}
