package com.jayantkrish.jklol.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * An Assignment represents a set of values assigned to a set of variables.
 * Assignments are immutable.
 */
public class Assignment implements Serializable {
  private static final long serialVersionUID = 2L;

  /**
   * The empty assignment, assigning no values to no variables.
   */
  public static final Assignment EMPTY = new Assignment(Arrays.asList(new Integer[] {}),
      Arrays.asList(new Object[] {}));

  private final int[] vars;
  private final Object[] values;

  /**
   * Creates an {@code Assignment} with the given variable values, and no
   * plates.
   * 
   * @param varNums
   * @param values
   */
  @Deprecated
  public Assignment(List<Integer> varNums, List<? extends Object> values) {
    Preconditions.checkNotNull(varNums);
    Preconditions.checkNotNull(values);
    Preconditions.checkArgument(varNums.size() == values.size());
    this.vars = new int[varNums.size()];
    this.values = new Object[varNums.size()];
    
    for (int i = 0; i < varNums.size(); i++) {
      this.vars[i] = varNums.get(i);
      this.values[i] = values.get(i);
    }

    ArrayUtils.sortKeyValuePairs(this.vars, this.values, 0, this.values.length);
  }

  /**
   * Creates an assignment to one variable.
   * 
   * @param varNum
   * @param value
   */
  public Assignment(int varNum, Object value) {
    vars = new int[] {varNum};
    values = new Object[] {value};
  }

  public Assignment(Map<Integer, Object> varValues) {
    varValueMap = new TreeMap<Integer, Object>(varValues);
  }

  /**
   * Copy constructor
   */
  public Assignment(Assignment a) {
    varValueMap = new TreeMap<Integer, Object>(a.varValueMap);
  }

  /**
   * Gets the number of variables with values in the assignment.
   */
  public int size() {
    return vars.length;
  }

  /**
   * Gets the indices of the variables in {@code this}, sorted in ascending
   * order.
   * 
   * @return
   */
  @Deprecated
  public List<Integer> getVariableNums() {
    return Ints.asList(vars);
  }

  public int[] getVariableNumsArray() {
    return vars;
  }

  /**
   * Gets the values assigned to the variables in {@code this}, in key order.
   * The ith element of the returned list is the value of the ith element of
   * {@code this.getVariableNums()}.
   * 
   * @return
   */
  @Deprecated
  public List<Object> getValues() {
    return Arrays.asList(values);
  }
  
  public Object[] getValuesArray() {
    return values;
  }

  /**
   * Gets the value assigned to the sole variable in {@code this}. Requires
   * {@code this.size() == 1}.
   * 
   * @return
   */
  public Object getOnlyValue() {
    Preconditions.checkState(values.length == 1);
    return values[0];
  }
  
  private final int getValueIndex(int varNum) {
    return Arrays.binarySearch(vars, varNum);
  }

  /**
   * Gets the value assigned to variable {@code varNum}. Returns {@code null} if
   * {@code varNum} does not have a value.
   * 
   * @param varNum
   * @return
   */
  public Object getValue(int varNum) {
    int index = getValueIndex(varNum);
    if (index < 0) {
      return null;
    } else {
      return values[index];
    }
  }

  /**
   * Returns {@code true} if {@code this} contains a value for {@code varNum}.
   * 
   * @param varNum
   * @return
   */
  public boolean contains(int varNum) {
    return getValueIndex(varNum) >= 0;
  }

  /**
   * Returns {@code true} if all variable numbers in {@code varNums} have a
   * value in {@code this}.
   * 
   * @param varNums
   * @return
   */
  @Deprecated
  public boolean containsAll(Collection<Integer> varNums) {
    return containsAll(Ints.toArray(varNums));
  }

  public boolean containsAll(int... varNums) {
    for (int varNum : varNums) {
      if (!contains(varNum)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if any variables in {@code varNums} are assigned values in
   * {@code this}.
   * 
   * @param varNums
   * @return
   */
  @Deprecated
  public boolean containsAny(Collection<Integer> varNums) {
    return containsAny(Ints.toArray(varNums));
  }

  public boolean containsAny(int... varNums) {
    for (int varNum : varNums) {
      if (contains(varNum)) {
        return true;
      }
    }
    return false;
  }

  /**
   * If varNums is a subset of the variables in this assignment, this method
   * returns the value assigned to each variable in varNums. varNums may contain
   * indices which are not represented in this assignment. These indices are
   * ignored; in this case, the returned assignment will contain fewer
   * variable/value mappings than {@code varNums.size()}.
   */
  @Deprecated
  public Assignment intersection(Collection<Integer> varNums) {
    return intersection(Ints.toArray(varNums));
  }
  
  public Assignment intersection(int ... varNums) {
    int[] newVarNums = new int[varNums.length];
    Object[] newValues = new Object[varNums.length];
    int numFilled = 0;
    for (int varNum : varNums) {
      int index = getValueIndex(varNum);
      if (index >= 0) {
        newVarNums[numFilled] = varNum;
        newValues[numFilled] = values[index];
        numFilled++;
      }
    }
    return Assignment.createFromUnsortedArrays(newVarNums, newValues, numFilled);
  }

  /**
   * Returns the subset of assignment whose variables are also contained in
   * vars. vars may not contain extra variables which are not part of this
   * assignment.
   */
  public Assignment intersection(VariableNumMap vars) {
    return intersection(vars.getVariableNumsArray());
  }

  /**
   * Combines two assignments into a single joint assignment to all of the
   * variables in each assignment. The two assignments must contain disjoint
   * sets of variables.
   */
  public Assignment union(Assignment other) {
    Preconditions.checkNotNull(other);
    // Merge varnums / values
    int[] otherNums = other.getVariableNumsArray();
    int[] myNums = getVariableNumsArray();
    Object[] otherVals = other.getValuesArray();
    Object[] myVals = getValuesArray();

    int[] mergedNums = new int[otherNums.length + myNums.length];
    Object[] mergedVals = new Object[otherNums.length + myNums.length];

    int i = 0;
    int j = 0;
    int numFilled = 0;
    while (i < otherNums.length && j < myNums.length) {
      if (otherNums[i] < myNums[j]) {
        mergedNums[numFilled] = otherNums[i];
        mergedVals[numFilled] = otherVals[i];
        i++;
        numFilled++;
      } else if (otherNums[i] > myNums[j]) {
        mergedNums[numFilled] = myNums[j];
        mergedVals[numFilled] = myVals[j];
        j++;
        numFilled++;
      } else {
        Preconditions.checkState(false, "Cannot combine non-disjoint assignments: %s with %s", this, other);
      }
    }
    // One list might still have elements in it.
    while (i < otherNums.length) {
      mergedNums[numFilled] = otherNums[i];
      mergedVals[numFilled] = otherVals[i];
      i++;
      numFilled++;
    }
    while (j < myNums.length) {
      mergedNums[numFilled] = myNums[j];
      mergedVals[numFilled] = myVals[j];
      j++;
      numFilled++;
    }

    return Assignment.createFromSortedArrays(mergedNums, mergedVals, numFilled);
  }

  /**
   * Returns a copy of this assignment without any assignments to the variable
   * numbers in varNumsToRemove
   * 
   * @param varNumsToRemove
   * @return
   */
  public Assignment removeAll(Collection<Integer> varNumsToRemove) {
    SortedMap<Integer, Object> newVarValueMap = new TreeMap<Integer, Object>(varValueMap);
    for (Integer varNum : varNumsToRemove) {
      if (newVarValueMap.containsKey(varNum)) {
        newVarValueMap.remove(varNum);
      }
    }
    return new Assignment(newVarValueMap);
  }
  
  public Assignment removeAll(int ... varNumsToRemove) {
    int[] newVarNums = Arrays.copyOf(vars, vars.length);
    
    int numRemoved = 0;
    for (int varNumToRemove : varNumsToRemove) {
      int index = getValueIndex(varNumToRemove);
      if (index >= 0) {
        newVarNums[index] = Integer.MIN_VALUE;
        numRemoved++;
      }
    }
    
    int[] finalVarNums = new int[newVarNums.length - numRemoved];
    Object[] finalValues = new Object[newVarNums.length - numRemoved];
    int numFilled = 0;
    for (int i = 0; i < newVarNums.length; i++) {
      if (newVarNums[i] != Integer.MIN_VALUE) {
        finalVarNums[numFilled] = newVarNums[i];
        finalValues[numFilled] = values[i];
        numFilled++;
      } 
    }
    Preconditions.checkState(numFilled == finalVarNums.length);
    return Assignment.createFromSortedArrays(finalVarNums, finalValues, numFilled);
  }

  /**
   * Return a new assignment where each var num has been replaced by its value
   * in varMap.
   */
  public Assignment mapVariables(Map<Integer, Integer> varMap) {
    int[] newVarNums = new int[vars.length];
    Object[] newValues = new Object[vars.length];

    int numFilled = 0;
    for (int i = 0; i < vars.length; i++) {
      if (varMap.containsKey(vars[i])) {
        newVarNums[numFilled] = varMap.get(vars[i]);
        newValues[numFilled] = values[i];
        numFilled++;
      }
    }
    return Assignment.createFromUnsortedArrays(newVarNums, newValues, numFilled);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(vars) * 31 + Arrays.hashCode(values);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Assignment) {
      Assignment a = (Assignment) o;
      return Arrays.equals(vars, a.vars) && Arrays.deepEquals(values, a.values);
    }
    return false;
  }

  @Override
  public String toString() {
    return Arrays.toString(vars) + "=" + Arrays.toString(values);
  }
  
  public String toXML() {
	  StringBuilder sb = new StringBuilder();
	  for (int i = 0; i < vars.length; i++) {
	    sb.append("<key>" + vars[i] + "</key>\n" + "<value>" + values[i] + "</value>\n");
	  }
	  return sb.toString();
  }

  /**
   * Computes the union of {@code assignments}. Equivalent to computing the
   * union iteratively using {@link #union(Assignment)}, but may be faster.
   * 
   * @param assignments
   * @return
   */
  public static Assignment unionAll(Collection<Assignment> assignments) {
    SortedMap<Integer, Object> newValues = Maps.newTreeMap();
    for (Assignment assignment : assignments) {
      for (Map.Entry<Integer, Object> varValue : assignment.varValueMap.entrySet()) {
        Preconditions.checkArgument(!newValues.containsKey(varValue.getKey()));
        newValues.put(varValue.getKey(), varValue.getValue());
      }
    }
    return new Assignment(newValues);
  }

  /**
   * Same as {{@link #unionAll(Collection)} with an array instead of a
   * Collection.
   * 
   * @param assignments
   * @return
   */
  public static Assignment unionAll(Assignment... assignments) {
    return Assignment.unionAll(Arrays.asList(assignments));
  }
}