package com.jayantkrish.jklol.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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
        // Ignore blank lines.
        if (line.trim().length() > 0) {
          lines.add(line);
        }
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

  public static List<String> readColumnFromDelimitedLines(Iterable<String> lines,
      int columnNumber, String delimiter) {
    List<String> columnValues = Lists.newArrayList();
    for (String line : lines) {
      String[] parts = line.split(delimiter);
      columnValues.add(parts[columnNumber]);
    }
    return columnValues;
  }

  /**
   * Returns a list of the unique values in {@code columnNumber} of 
   * {@code filename}. The columns of {@code filename} are delimited 
   * by {@code delimiter}.
   * 
   * @param filename
   * @param columnNumber
   * @param delimiter
   * @return
   */
  public static List<String> readUniqueColumnValuesFromDelimitedFile(String filename, 
      int columnNumber, String delimiter) {
    Set<String> values = Sets.newHashSet(
        readColumnFromDelimitedFile(filename, columnNumber, delimiter));
    return Lists.newArrayList(values);
  }

  /**
   * Serializes {@code object} into {@code filename}.
   * 
   * @param filename
   * @param object
   */
  public static void serializeObjectToFile(Object object, String filename) {
    FileOutputStream fos = null;
    ObjectOutputStream out = null;
    try {
      fos = new FileOutputStream(filename);
      out = new ObjectOutputStream(fos);
      out.writeObject(object);
      out.close();
    } catch(IOException ex) {
      ex.printStackTrace();
      System.exit(1);
    }    
  }

  public static <T> T readSerializedObject(String filename, Class<T> clazz) {
    // Read in the serialized model.
    T object = null;
    FileInputStream fis = null;
    ObjectInputStream in = null;
    try {
      fis = new FileInputStream(filename);
      in = new ObjectInputStream(fis);
      object = clazz.cast(in.readObject());
      in.close();
    } catch(IOException ex) {
      throw new RuntimeException(ex);
    } catch(ClassNotFoundException ex) {
      throw new RuntimeException(ex);
    }
    return object;
  }
}