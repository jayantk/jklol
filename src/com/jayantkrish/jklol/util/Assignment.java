package com.jayantkrish.jklol.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * An Assignment represents a set of values assigned to a set of variables,
 * possibly nested within plates. Assignments are immutable.
 */
public class Assignment {

  public static final Assignment EMPTY = new Assignment(Arrays.asList(new Integer[] {}),
      Arrays.asList(new Object[] {}));

  private SortedMap<Integer, Object> varValueMap;

  /**
   * Creates an {@code Assignment} with the given variable values, and no
   * plates.
   * 
   * @param varNums
   * @param values
   */
  public Assignment(List<Integer> varNums, List<? extends Object> values) {
    Preconditions.checkNotNull(varNums);
    Preconditions.checkNotNull(values);
    Preconditions.checkArgument(varNums.size() == values.size());
    varValueMap = new TreeMap<Integer, Object>();
    for (int i = 0; i < varNums.size(); i++) {
      varValueMap.put(varNums.get(i), values.get(i));
    }
  }

  public Assignment(int varNum, Object value) {
    varValueMap = new TreeMap<Integer, Object>();
    varValueMap.put(varNum, value);
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
    return varValueMap.size();
  }

  /**
   * Gets the indices of the variables in {@code this}, sorted in ascending
   * order.
   * 
   * @return
   */
  public List<Integer> getVariableNums() {
    return new ArrayList<Integer>(varValueMap.keySet());
  }

  /**
   * Gets the values assigned to the variables in {@code this}, in key order.
   * The ith element of the returned list is the value of the ith element of
   * {@code this.getVariableNums()}.
   * 
   * @return
   */
  public List<Object> getValues() {
    return new ArrayList<Object>(varValueMap.values());
  }

  /**
   * Gets the value assigned to the sole variable in {@code this}. Requires
   * {@code this.size() == 1}.
   * 
   * @return
   */
  public Object getOnlyValue() {
    Preconditions.checkState(varValueMap.size() == 1);
    return varValueMap.values().iterator().next();
  }

  /**
   * Gets the value assigned to variable {@code varNum}. Returns {@code null} if
   * {@code varNum} does not have a value.
   * 
   * @param varNum
   * @return
   */
  public Object getValue(int varNum) {
    return varValueMap.get(varNum);
  }

  /**
   * Returns {@code true} if {@code this} contains a value for {@code varNum}.
   * 
   * @param varNum
   * @return
   */
  public boolean contains(int varNum) {
    return varValueMap.containsKey(varNum);
  }

  /**
   * Returns {@code true} if all variable numbers in {@code varNums} have a
   * value in {@code this}.
   * 
   * @param varNums
   * @return
   */
  public boolean containsAll(Collection<Integer> varNums) {
    for (Integer varNum : varNums) {
      if (!varValueMap.containsKey(varNum)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns {@code true} if {@code this} contains all of the mappings in
   * {@code assignment}. Note that this checks both variable numbers and their
   * values.
   * 
   * @param assignment
   * @return
   */
  public boolean containsAll(Assignment assignment) {
    for (Integer variableNum : assignment.getVariableNums()) {
      if (!varValueMap.containsKey(variableNum) ||
          getValue(variableNum) != assignment.getValue(variableNum)) {
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
  public boolean containsAny(Collection<Integer> varNums) {
    for (Integer varNum : varNums) {
      if (varValueMap.containsKey(varNum)) {
        return true;
      }
    }
    return false;
  }

  /**
   * If varNums is a subset of the variables in this assignment, this method
   * returns the value assigned to each variable in varNums. Puts the return
   * value into "returnValue" if it is non-null, otherwise allocates and returns
   * a new list.
   */
  public List<Object> intersection(List<Integer> varNums,
      List<Object> returnValue) {
    List<Object> retVal = returnValue;
    if (retVal == null) {
      retVal = new ArrayList<Object>();
    }

    for (Integer varNum : varNums) {
      retVal.add(varValueMap.get(varNum));
    }
    return retVal;
  }

  /**
   * If varNums is a subset of the variables in this assignment, this method
   * returns the value assigned to each variable in varNums. varNums may contain
   * indices which are not represented in this assignment. These indices are
   * ignored; in this case, the returned assignment will contain fewer
   * variable/value mappings than {@code varNums.size()}.
   */
  public Assignment intersection(Collection<Integer> varNums) {
    List<Integer> varNumList = new ArrayList<Integer>();
    List<Object> retVal = new ArrayList<Object>();
    for (Integer varNum : varNums) {
      if (varValueMap.containsKey(varNum)) {
        varNumList.add(varNum);
        retVal.add(varValueMap.get(varNum));
      }
    }
    return new Assignment(varNumList, retVal);
  }

  /**
   * Returns the subset of assignment whose variables are also contained in
   * vars. vars may not contain extra variables which are not part of this
   * assignment.
   */
  public Assignment intersection(VariableNumMap vars) {
    return intersection(vars.getVariableNums());
  }

  /**
   * Combines two assignments into a single joint assignment to all of the
   * variables in each assignment. The two assignments must contain disjoint
   * sets of variables.
   */
  public Assignment union(Assignment other) {
    Preconditions.checkNotNull(other);
    // Merge varnums / values
    List<Integer> otherNums = other.getVariableNums();
    List<Integer> myNums = getVariableNums();
    List<Object> otherVals = other.getValues();
    List<Object> myVals = getValues();

    List<Integer> mergedNums = new ArrayList<Integer>();
    List<Object> mergedVals = new ArrayList<Object>();

    int i = 0;
    int j = 0;
    while (i < otherNums.size() && j < myNums.size()) {
      if (otherNums.get(i) < myNums.get(j)) {
        mergedNums.add(otherNums.get(i));
        mergedVals.add(otherVals.get(i));
        i++;
      } else if (otherNums.get(i) > myNums.get(j)) {
        mergedNums.add(myNums.get(j));
        mergedVals.add(myVals.get(j));
        j++;
      } else {
        Preconditions.checkState(false, "Cannot combine non-disjoint assignments: %s with %s", this, other);
      }
    }
    // One list might still have elements in it.
    while (i < otherNums.size()) {
      mergedNums.add(otherNums.get(i));
      mergedVals.add(otherVals.get(i));
      i++;
    }
    while (j < myNums.size()) {
      mergedNums.add(myNums.get(j));
      mergedVals.add(myVals.get(j));
      j++;
    }

    return new Assignment(mergedNums, mergedVals);
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

  /**
   * Return a new assignment where each var num has been replaced by its value
   * in varMap.
   */
  public Assignment mapVariables(Map<Integer, Integer> varMap) {
    List<Integer> newVarNums = new ArrayList<Integer>();
    List<Object> newVarVals = new ArrayList<Object>();
    for (Integer k : varValueMap.keySet()) {
      if (varMap.containsKey(k)) {
        newVarNums.add(varMap.get(k));
        newVarVals.add(varValueMap.get(k));
      }
    }
    return new Assignment(newVarNums, newVarVals);
  }

  @Override
  public int hashCode() {
    return varValueMap.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Assignment) {
      Assignment a = (Assignment) o;
      return varValueMap.equals(a.varValueMap);
    }
    return false;
  }

  @Override
  public String toString() {
    return varValueMap.keySet().toString() + "=" + varValueMap.values().toString();
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