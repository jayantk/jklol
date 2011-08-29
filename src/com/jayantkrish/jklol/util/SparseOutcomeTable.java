package com.jayantkrish.jklol.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;

/**
 * A SparseOutcomeTable sparsely stores a mapping from Assignments to whatever
 * you want.
 */
public class SparseOutcomeTable<T> {

  private List<Integer> varNums;
  private Map<List<Object>, T> outcomes;

  // An index storing assignments containing particular variable values.
  private List<HashMultimap<Object, Assignment>> varValueAssignmentIndex;

  public SparseOutcomeTable(List<Integer> varNums) {
    this.varNums = new ArrayList<Integer>(varNums);
    Collections.sort(this.varNums);

    outcomes = new HashMap<List<Object>, T>();

    varValueAssignmentIndex = new ArrayList<HashMultimap<Object, Assignment>>(varNums.size());
    for (int i = 0; i < varNums.size(); i++) {
      varValueAssignmentIndex.add(new HashMultimap<Object, Assignment>());
    }
  }

  /**
   * Return the variable numbers stored in this table, in sorted order.
   */
  public List<Integer> getVarNums() {
    return Collections.unmodifiableList(this.varNums);
  }

  public void put(Assignment key, T outcome) {
    for (int i = 0; i < varNums.size(); i++) {
      varValueAssignmentIndex.get(i).put(key.getVarValue(varNums.get(i)), key);
    }
    outcomes.put(key.getVarValuesInKeyOrder(), outcome);
  }

  /**
   * Gets the number of assignments stored in {@code this}.
   * 
   * @return
   */
  public int size() {
    return outcomes.size();
  }

  /**
   * Returns {@code true} if {@code key} has a value in this. Equivalent to
   * {@code this.get(key) != null}.
   * 
   * @param key
   * @return
   */
  public boolean containsKey(Assignment key) {
    return outcomes.containsKey(key.getVarValuesInKeyOrder());
  }

  /**
   * Get the value associated with a variable assignment. Returns {@code null}
   * if no value is associated with {@code key}.
   */
  public T get(Assignment key) {
    Preconditions.checkArgument(key.getVarNumsSorted().equals(varNums));
    return outcomes.get(key.getVarValuesInKeyOrder());
  }

  /**
   * Gets the assignments in this table where
   * {@code assignment.subAssignment(variableNum)} is an element of
   * {@code values}.
   * 
   * @param variableNum
   * @param value
   * @return
   */
  public Set<Assignment> getKeysWithVariableValue(int variableNum, Set<Object> values) {
    int varIndex = -1;
    for (int i = 0; i < varNums.size(); i++) {
      if (varNums.get(i) == variableNum) {
        varIndex = i;
        break;
      }
    }
    Preconditions.checkArgument(varIndex != -1);

    Set<Assignment> possibleAssignments = new HashSet<Assignment>();
    for (Object value : values) {
      possibleAssignments.addAll(varValueAssignmentIndex.get(varIndex).get(value));
    }
    return possibleAssignments;
  }

  /**
   * Returns an iterator over all assignments (keys) in this table.
   */
  public Iterator<Assignment> assignmentIterator() {
    return new AssignmentIterator(this);
  }

  public String toString() {
    return outcomes.toString();
  }

  /**
   * Helper class for iterating over assignments.
   */
  public class AssignmentIterator implements Iterator<Assignment> {

    private Iterator<List<Object>> varValueIndexIterator;
    private List<Integer> varNums;

    public AssignmentIterator(SparseOutcomeTable<?> table) {
      varValueIndexIterator = table.outcomes.keySet().iterator();
      varNums = table.varNums;
    }

    public boolean hasNext() {
      return varValueIndexIterator.hasNext();
    }

    public Assignment next() {
      return new Assignment(varNums, varValueIndexIterator.next());
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}