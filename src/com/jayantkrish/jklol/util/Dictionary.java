package com.jayantkrish.jklol.util;

/**
 * A mapping from objects to integers. Dictionaries are typically used
 * to save storage space by maintaining only one copy of equivalent
 * objects.
 * 
 * @author jayantk
 */
public interface Dictionary<T> {

  void add(T item);
  
  void addAll(Iterable<T> items);
}
