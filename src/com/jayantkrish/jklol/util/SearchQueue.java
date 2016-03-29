package com.jayantkrish.jklol.util;

public interface SearchQueue<T> {

  public void offer(T item, double score);
  
  public int size();
  
  public T[] getItems();
  
  public void clear();
}
