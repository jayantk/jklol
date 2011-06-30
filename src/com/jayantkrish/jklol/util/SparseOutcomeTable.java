package com.jayantkrish.jklol.util;

import java.util.*;

/**
 * A SparseOutcomeTable sparsely stores a mapping from
 * Assignments to whatever you want.
 */
public class SparseOutcomeTable<T> {

	private List<Integer> varNums;
	private Map<List<Object>, T> outcomes;

	public SparseOutcomeTable(List<Integer> varNums) {
		this.varNums = new ArrayList<Integer>(varNums);
		Collections.sort(this.varNums);

		outcomes = new HashMap<List<Object>, T>();
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