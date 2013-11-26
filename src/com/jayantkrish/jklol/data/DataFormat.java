package com.jayantkrish.jklol.data;

import java.util.List;

/**
 * A machine-readable string data format for objects of
 * type {@code T}. This interface provides methods for parsing 
 * string objects and producing objects of type {@code T}.
 * 
 * @author jayant
 *
 * @param <T> Type of object which can be read using this format.
 */
public interface DataFormat<T> {

  public T parseFrom(String item);

  public List<T> parseFromFile(String filename);
}
