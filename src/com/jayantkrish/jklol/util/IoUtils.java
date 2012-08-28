package com.jayantkrish.jklol.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;

public class IoUtils {

  /**
   * Read the lines of a file into a list of strings, with each line represented
   * as its own string.
   * 
   * @param filename
   * @return
   */
  public static List<String> readLines(String filename) {
    List<String> lines = Lists.newArrayList();
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;
      while ((line = in.readLine()) != null) {
        lines.add(line);
      }
      in.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return lines;
  }

  /**
   * Counts the number of columns in a file delimited by {@code delimiter}.
   * Assumes that the first line of the file is representative of the file as a
   * whole.
   * 
   * @param filename
   * @param delimiter
   * @return
   */
  public static int getNumberOfColumnsInFile(String filename, String delimiter) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;
      while ((line = in.readLine()) != null) {
        String[] parts = line.split(delimiter);
        in.close();
        return parts.length;
      }
      in.close();
      // File is empty.
      return 0;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Reads in the values from a particular column of {@code filename}, as
   * delimited by {@code delimiter}.
   * 
   * @param filename
   * @param columnNumber
   * @param delimiter
   * @return
   */
  public static List<String> readColumnFromDelimitedFile(String filename, 
      int columnNumber, String delimiter) {
    List<String> columnValues = Lists.newArrayList();
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;
      while ((line = in.readLine()) != null) {
        String[] parts = line.split(delimiter);
        columnValues.add(parts[columnNumber]);
      }
      in.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return columnValues;
  }
}