package com.jayantkrish.jklol.util;

import java.util.List;

import com.google.common.collect.Lists;

/**
 * Utilities for manipulating (collections of) strings.
 *
 * @author jayantk
 */
public class StringUtils {

  public static List<String> readColumnFromDelimitedLines(Iterable<String> lines,
      int columnNumber, String delimiter) {
    List<String> columnValues = Lists.newArrayList();
    for (String line : lines) {
      String[] parts = line.split(delimiter);
      columnValues.add(parts[columnNumber]);
    }
    return columnValues;
  }
}