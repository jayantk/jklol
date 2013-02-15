package com.jayantkrish.jklol.models;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Represents a discrete random variable type which takes on one value from a
 * finite set of values. Variables are immutable.
 * 
 * @author jayant
 */
public class DiscreteVariable implements Variable, Serializable {

  private static final long serialVersionUID = 2948903432256540126L;
  
  private String name;
  private IndexedList<Object> values;

  public DiscreteVariable(String name, Collection<? extends Object> values) {
    this.name = name;
    this.values = IndexedList.create(values);
  }

  /**
   * Creates a discrete variable whose values are {@code 0} to
   * {@code size - 1}.
   * 
   * @param maxNum
   * @return
   */
  public static DiscreteVariable sequence(String name, int size) {
    List<Integer> values = Lists.newArrayList();
    for (int i = 0; i < size; i++) {
      values.add(i);
    }
    return new DiscreteVariable(name, values);
  }
  
  /**
   * Constructs a variable containing all of the values in {@code columnNumber}
   * of the delimited file {@code filename}. {@code delimiter} separates the
   * columns of the file.
   *
   * @param variableName
   * @param filename
   * @param delimiter
   * @param columnNumber
   * @return
   */
  public static DiscreteVariable fromCsvColumn(String variableName, 
      String filename, String delimiter, int columnNumber) {
    List<String> values = IoUtils.readColumnFromDelimitedFile(filename, columnNumber, delimiter);
    return new DiscreteVariable(variableName, values);
  }
  
  @Override
  public String getName() {
    return name;
  }

  @Override
  public Object getArbitraryValue() {
    return values.get(0);
  }

  @Override
  public boolean canTakeValue(Object value) {
    return values.contains(value);
  }

  /**
   * Get the number of possible values that this variable can take on.
   * 
   * @return
   */
  public int numValues() {
    return values.size();
  }

  /**
   * Gets all possible values for this variable.
   * 
   * @return
   */
  public List<Object> getValues() {
    return values.items();
  }

  public <T> List<T> getValuesWithCast(Class<T> clazz) {
    List<T> castedValues = Lists.newArrayList();
    for (Object value : values) {
      castedValues.add(clazz.cast(value));
    }
    return castedValues;
  }

  /**
   * Get the value of this enum with the passed-in index.
   * 
   * @param index
   * @return
   */
  public Object getValue(int index) {
    return values.get(index);
  }
  
  public Object[] getValueArray(int[] indexes) {
    Object[] valueArray = new Object[indexes.length];
    for (int i = 0; i < indexes.length; i++) {
      valueArray[i] = getValue(indexes[i]);
    }
    return valueArray;
  }

  /**
   * Get an integer index which represents the passed in value. Throws a
   * {@code NoSuchElementException} if value is not among the set of values this
   * variable can be assigned.
   */
  public int getValueIndex(Object value) {
    if (!values.contains(value)) {
      throw new NoSuchElementException("Tried accessing nonexistent value \"" + value
          + "\" of variable " + name);
    }
    return values.getIndex(value);
  }

  @Override
  public String toString() {
    return name + " (" + values.size() + " values)";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof DiscreteVariable) {
      DiscreteVariable v = (DiscreteVariable) o;
      return name.equals(v.name) && values.equals(v.values);
    }
    return false;
  }
}