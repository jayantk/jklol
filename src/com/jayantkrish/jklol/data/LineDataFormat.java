package com.jayantkrish.jklol.data;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.IoUtils;

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
