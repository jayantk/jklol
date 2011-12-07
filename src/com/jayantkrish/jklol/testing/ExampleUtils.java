package com.jayantkrish.jklol.testing;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;

/**
 * Static methods for instantiating lists of {@link Example}s.
 * 
 * @author jayantk
 */
public class ExampleUtils {

  /**
   * Converts {@code data} into a list of {@code Example}s. Each element of
   * {@code data} should be an array of length 2, where the first value is the
   * inputVar and the second value is the outputVar of the corresponding example.
   * 
   * @param data
   * @return
   */
  public static List<Example<String, String>> exampleArrayToList(String[][] data) {
    List<Example<String, String>> pairs = Lists.newArrayList();
    for (int i = 0; i < data.length; i++) {
      pairs.add(new Example<String, String>(data[i][0], data[i][1]));
    }
    return pairs;
  }

  private ExampleUtils() {
    // Prevent instantiation.
  }
}
