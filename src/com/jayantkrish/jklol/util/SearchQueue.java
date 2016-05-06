package com.jayantkrish.jklol.util;

public interface SearchQueue<T> {

  public T offer(T item, double score);
  
  public int size();
  
  public T[] getItems();
  
  public void clear();
}
