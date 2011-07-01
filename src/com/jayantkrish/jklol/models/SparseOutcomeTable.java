package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.HashMultimap;

import java.util.*;
import java.io.Serializable;

/**
 * A SparseOutcomeTable sparsely stores a mapping from
 * Assignments to whatever you want.
 */
public class SparseOutcomeTable<T> implements Serializable {

    private List<Integer> varNums;
    private Map<List<Integer>, T> outcomes;

    // An index storing assignments containing particular variable values.
    private List<HashMultimap<Integer, Assignment>> varValueAssignmentIndex;

    public SparseOutcomeTable(List<Integer> varNums) {
	this.varNums = new ArrayList<Integer>(varNums);
	Collections.sort(this.varNums);
	
	outcomes = new HashMap<List<Integer>, T>();
	varValueAssignmentIndex = new ArrayList<HashMultimap<Integer, Assignment>>();
	for (int i = 0; i < varNums.size(); i++) {
	    varValueAssignmentIndex.add(new HashMultimap<Integer, Assignment>());
	}
    }

    /**
     * Return the variable numbers stored in this table,
     * in sorted order.
     */
    public List<Integer> getVarNums() {
	return Collections.unmodifiableList(this.varNums);
    }

    /**
     * Assign an outcome to the given assignment key.
     */
    public void put(Assignment key, T outcome) {
	assert key.getVarNumsSorted().equals(varNums);
	outcomes.put(key.getVarValuesInKeyOrder(), outcome);
	
	Assignment copy = new Assignment(key);
	List<Integer> varNums = getVarNums();
	for (int i = 0; i < varNums.size(); i++) {
	    varValueAssignmentIndex.get(i).put(copy.getVarValue(varNums.get(i)), copy);
	}
    }

    /**
     * Returns all of the keys in this table where varNum has a value in varValues.
     */
    public Set<Assignment> getKeysWithVarValue(int varNum, Set<Integer> varValues) {
	int varIndex = -1;
	List<Integer> varNums = getVarNums();
	for (int i = 0; i < varNums.size(); i++) {
	    if (varNums.get(i) == varNum) {
		varIndex = i;
		break;
	    }
	}
	assert varIndex != -1;

	Set<Assignment> possibleAssignments = new HashSet<Assignment>();
	for (Integer varValue : varValues) {
	    possibleAssignments.addAll(varValueAssignmentIndex.get(varIndex).get(varValue));
	}
	return possibleAssignments;
    }

    public boolean containsKey(Assignment key) {
	return outcomes.containsKey(key.getVarValuesInKeyOrder());
    }

    /**
     * Get the value associated with a variable assignment.
     */
    public T get(Assignment key) {
	return outcomes.get(key.getVarValuesInKeyOrder());
    }

    /**
     * key contains variable values in sorted order by variable num.
     */ 
    public T get(List<Integer> key) {
	return outcomes.get(key);
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

	private Iterator<List<Integer>> varValueIndexIterator;
	private SparseOutcomeTable table;
	private Assignment a;
	private List<Integer> varNums;

	public AssignmentIterator(SparseOutcomeTable table) {
	    this.table = table;
	    varValueIndexIterator = table.outcomes.keySet().iterator();
	    a = null;
	    varNums = table.varNums;
	}

	public boolean hasNext() {
	    return varValueIndexIterator.hasNext();
	}

	public Assignment next() {
	    List<Integer> nextValue = varValueIndexIterator.next();
	    if (a == null) {
		a = new Assignment(varNums, nextValue);
	    } else {
		for (int i = 0; i < varNums.size(); i++) {
		    a.setVarValue(varNums.get(i), nextValue.get(i));
		}
	    }
	    return a;
	    // return new Assignment(varNums, nextValue);
	}

	public void remove() {
	    throw new UnsupportedOperationException();
	}
    }
}