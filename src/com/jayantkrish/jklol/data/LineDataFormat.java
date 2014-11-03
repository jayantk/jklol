package com.jayantkrish.jklol.data;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * A data format where each individual object is provided on a single
 * line. This class implements common methods for reading collections
 * of objects from multi-line strings. 
 * 
 * @author jayant
 *
 * @param <T> Type of object which can be read using this format.
 */
public abstract class LineDataFormat<T> implements DataFormat<T> {
  
  @Override
  public List<T> parseFromFile(String filename) {
    List<T> examples = Lists.newArrayList();
    for (String line : IoUtils.readLines(filename)) {
      T example = parseFrom(line);
      examples.add(example);
    }
    return examples;
  }
}
