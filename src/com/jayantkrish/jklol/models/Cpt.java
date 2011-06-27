package com.jayantkrish.jklol.models;

import java.util.*;

/**
 * A conditional probability table (for a BN). Stores a conditional probability over a set of children
 * conditioned on a set of parents. This CPT is stored sparsely.
 *
 * Also stores sufficient statistics for estimating a CPT.
 */ 
public class Cpt {

	// Child / parent variables define possible outcomes.
	private List<Variable<?>> childVars;
	private List<Variable<?>> parentVars;
	private List<Integer> parentNums;
	private List<Integer> childNums;

	private List<Integer> allNums;
	private List<Variable<?>> allVars;

	// TODO: Maybe these should be dense? It's unclear...
	protected SparseOutcomeTable<Double> childStatistics;
	protected SparseOutcomeTable<Double> parentStatistics;

	public Cpt(List<Variable<?>> parents, List<Variable<?>> children) {
		childVars = children;
		parentVars = parents;

		allNums = new ArrayList<Integer>();
		allVars = new ArrayList<Variable<?>>();
		for (int i = 0; i < parentVars.size() + childVars.size(); i++) {
			allNums.add(i);
		}

		parentNums = new ArrayList<Integer>();
		childNums = new ArrayList<Integer>();
		for (int i = 0; i < parents.size(); i++) {
			parentNums.add(i);
			allVars.add(parents.get(i));
		}
		for (int i = 0; i < children.size(); i++) {
			childNums.add(i + parents.size());
			allVars.add(children.get(i));
		}

		childStatistics = new SparseOutcomeTable<Double>(allNums);
		parentStatistics = new SparseOutcomeTable<Double>(parentNums);
	}

	/**
	 * Set the number of times a particular assignment has been observed.
	 */
	public void setOutcomeCount(Assignment a, double count) {
		double oldCount = 0.0;
		if (childStatistics.containsKey(a)) {
			oldCount = childStatistics.get(a);
		}
		childStatistics.put(a, count);

		Assignment subAssignment = a.subAssignment(parentNums);
		if (!parentStatistics.containsKey(subAssignment)) {
			parentStatistics.put(subAssignment, 0.0);
		}
		parentStatistics.put(subAssignment,
				parentStatistics.get(subAssignment) + count - oldCount);
	}

	/**
	 * Clear all outcome counts in this CPT.
	 */
	public void clearOutcomeCounts() {
		Iterator<Assignment> assignmentIterator = childStatistics.assignmentIterator();
		while (assignmentIterator.hasNext()) {
			Assignment a = assignmentIterator.next();
			childStatistics.put(a, 0.0);
		}

		assignmentIterator = parentStatistics.assignmentIterator();
		while (assignmentIterator.hasNext()) {
			Assignment a = assignmentIterator.next();
			parentStatistics.put(a, 0.0);
		}
	}

	/**
	 * Add some number of occurrences to a particular outcome.
	 */
	public void incrementOutcomeCount(Assignment a, double count) {
		double oldCount = 0.0;
		if (childStatistics.containsKey(a)) {
			oldCount = childStatistics.get(a);
		}
		childStatistics.put(a, count + oldCount);

		Assignment subAssignment = a.subAssignment(parentNums);
		if (!parentStatistics.containsKey(subAssignment)) {
			parentStatistics.put(subAssignment, 0.0);
		}

		parentStatistics.put(subAssignment,
				parentStatistics.get(subAssignment) + count);
	}

	public void addUniformSmoothing(double virtualCounts) {
		Iterator<Assignment> assignmentIter = assignmentIterator();
		while (assignmentIter.hasNext()) {
			setOutcomeCount(assignmentIter.next(), virtualCounts);
		}
	}

	/**
	 * Get the probability of a particular assignment.
	 */
	public double getProbability(Assignment a) {
		Assignment subAssignment = a.subAssignment(parentNums);
		if (!parentStatistics.containsKey(subAssignment)) {
			throw new RuntimeException("Cannot get conditional probability for unobserved parents: " + subAssignment);
		}
		if (!childStatistics.containsKey(a)) {
			return 0.0;
		}
		return childStatistics.get(a) / parentStatistics.get(subAssignment);
	}

	public Iterator<Assignment> assignmentIterator() {
		return new AllAssignmentIterator(allNums, allVars);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		Iterator<Assignment> iter = assignmentIterator();
		List<Object> parentValues = new ArrayList<Object>();
		List<Object> childValues = new ArrayList<Object>();
		while (iter.hasNext()) {
			Assignment a = iter.next();
			parentValues.clear();
			childValues.clear();
			for (int i = 0; i < parentNums.size(); i++) {
				parentValues.add(a.getVarValue(parentNums.get(i)));
			}
			for (int i = 0; i < childNums.size(); i++) {
				childValues.add(a.getVarValue(childNums.get(i)));
			}

			sb.append(parentValues);
			sb.append("-->");
			sb.append(childValues);
			sb.append(":");
			sb.append(getProbability(a));
			sb.append("\n");
		}
		return sb.toString();
	}
}