package com.jayantkrish.jklol.models;

import java.util.*;

/**
 * A SparseOutcomeTable sparsely stores a mapping from
 * Assignments to whatever you want.
 */
public class SparseOutcomeTable<T> {

    private List<Integer> varNums;
    private Map<List<Integer>, T> outcomes;

    public SparseOutcomeTable(List<Integer> varNums) {
	this.varNums = new ArrayList<Integer>(varNums);
	Collections.sort(this.varNums);
	
	outcomes = new HashMap<List<Integer>, T>();
    }

    /**
     * Return the variable numbers stored in this table,
     * in sorted order.
     */
    public List<Integer> getVarNums() {
	return Collections.unmodifiableList(this.varNums);
    }

    public void put(Assignment key, T outcome) {
	assert key.getVarNumsSorted().equals(varNums);
	outcomes.put(key.getVarValuesInKeyOrder(), outcome);
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