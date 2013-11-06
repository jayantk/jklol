package com.jayantkrish.jklol.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
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
  public static final Assignment EMPTY = new Assignment(new int[0], new Object[0]); 

  private final int[] vars;
  private final Object[] values;
  
  private Assignment(int[] vars, Object[] values) {
    this.vars = Preconditions.checkNotNull(vars);
    this.values = Preconditions.checkNotNull(values);
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

  /**
   * Creates an {@code Assignment} mapping each variable in {@code vars} to the value
   * at the corresponding index of {@code values}. {@code vars} does not need 
   * to be sorted in any order. This method does not copy {@code vars} or
   * {@code values} and may modify either array; the caller should not read
   * or modify either of these arrays after invoking this method.
   *  
   * @param vars
   * @param values
   * @return
   */
  public static final Assignment fromUnsortedArrays(int[] vars, Object[] values) {
    ArrayUtils.sortKeyValuePairs(vars, new Object[][] {values}, 0, vars.length);
    return fromSortedArrays(vars, values);
  }

  /**
   * Creates an {@code Assignment} mapping each variable in {@code vars} to
   * the value at the corresponding index of {@code values}. {@code vars} 
   * must be sorted in ascending order. This method does not copy either
   * {@code vars} or {@code values}; the caller should not read or modify
   * either of these arrays after invoking this method.
   *  
   * @param vars
   * @param values
   * @return
   */
  public static final Assignment fromSortedArrays(int[] vars, Object[] values) {
    // Verify that the assignment is sorted and contains no duplicate values.
    for (int i = 1; i < vars.length; i++) {
      Preconditions.checkArgument(vars[i - 1] < vars[i], "Illegal assignment variable nums: %s %s",
          vars[i - 1], vars[i]);
    }
    return new Assignment(vars, values);
  }
  
  public static final Assignment fromMap(Map<Integer, Object> varValueMap) {
    int[] varNums = new int[varValueMap.size()];
    Object[] values = new Object[varValueMap.size()];
    int i = 0;
    for (Map.Entry<Integer, Object> entry : varValueMap.entrySet()) {
      varNums[i] = entry.getKey();
      values[i] = entry.getValue();
      i++;
    }
    return Assignment.fromUnsortedArrays(varNums, values);
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
    
    if (numFilled < newVarNums.length) {
      newVarNums = Arrays.copyOf(newVarNums, numFilled);
      newValues = Arrays.copyOf(newValues, numFilled);
    }
    return Assignment.fromUnsortedArrays(newVarNums, newValues);
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
    Preconditions.checkState(numFilled == mergedNums.length);
    return Assignment.fromSortedArrays(mergedNums, mergedVals);
  }

  /**
   * Returns a copy of this assignment without any assignments to the variable
   * numbers in varNumsToRemove
   * 
   * @param varNumsToRemove
   * @return
   */
  @Deprecated
  public Assignment removeAll(Collection<Integer> varNumsToRemove) {
    return removeAll(Ints.toArray(varNumsToRemove));
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
    return Assignment.fromSortedArrays(finalVarNums, finalValues);
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
    
    if (numFilled < newVarNums.length) {
      newVarNums = Arrays.copyOf(newVarNums, numFilled);
      newValues = Arrays.copyOf(newValues, numFilled);
    }
    return Assignment.fromUnsortedArrays(newVarNums, newValues);
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
    int numVars = 0;
    for (Assignment assignment : assignments) {
      numVars += assignment.size();
    }

    int[] vars = new int[numVars];
    Object[] values = new Object[numVars];
    int numFilled = 0;
    for (Assignment assignment : assignments) {
      int[] assignmentVars = assignment.vars;
      Object[] assignmentValues = assignment.values;
      int numAssignmentVars = assignment.vars.length;
      for (int i = 0; i < numAssignmentVars; i++) {
        vars[numFilled] = assignmentVars[i];
        values[numFilled] = assignmentValues[i];
        numFilled++;
      }
    }
    Preconditions.checkState(numFilled == numVars);
    return Assignment.fromUnsortedArrays(vars, values);
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