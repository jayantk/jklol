package com.jayantkrish.jklol.data;

import java.util.List;

public interface DataFormat<T> {
  
  public T parseFrom(String item);

  public List<T> parseFromFile(String filename);
}
