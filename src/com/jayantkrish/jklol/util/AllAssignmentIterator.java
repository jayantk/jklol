package com.jayantkrish.jklol.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * An iterator over all possible assignments to a set of variables.
 */
public class AllAssignmentIterator implements Iterator<Assignment> {

	private VariableNumMap<DiscreteVariable> vars;
	private List<Integer> currentValueInds;
	private List<Integer> finalValueInds;
	private List<Object> currentValues;

	public AllAssignmentIterator(List<Integer> varNums, List<DiscreteVariable> varList) {
		this.vars = new VariableNumMap<DiscreteVariable>(varNums, varList);
		initializeValueState();
	}

	public AllAssignmentIterator(VariableNumMap<DiscreteVariable> varNumMap) {
		this.vars = varNumMap;
		initializeValueState();
	}

	/*
	 * Initializes the variable values controlling the iteration position. 
	 */
	private void initializeValueState() {
		this.currentValueInds = new ArrayList<Integer>(vars.size());
		this.currentValues = new ArrayList<Object>(vars.size());
		this.finalValueInds = new ArrayList<Integer>(vars.size());
		for (Integer varNum : vars.getVariableNums()) {
			currentValueInds.add(0);
			currentValues.add(null);
			finalValueInds.add(vars.getVariable(varNum).numValues() - 1);
		}
		// Set the last index to one higher than the actual number of values; when we increment
		// currentValues to this point, we will be done.
		finalValueInds.set(vars.size() - 1, finalValueInds.get(vars.size() - 1) + 1);    	
	}

	public boolean hasNext() {
		return !(currentValueInds.get(vars.size() - 1).equals(finalValueInds.get(vars.size() - 1)));
	}

	public Assignment next() {
		Assignment a = getCurrentAssignment();
		incrementCurrentValueInds();
		return a;
	}

	/*
	 * Translates currentValueInds into an assignment.
	 */
	private Assignment getCurrentAssignment() {
		List<Integer> varNums = vars.getVariableNums();
		for (int i = 0; i < currentValueInds.size(); i++) {
			currentValues.set(i, vars.getVariable(varNums.get(i)).getValue(currentValueInds.get(i)));
		}
		return new Assignment(vars.getVariableNums(), currentValues);
	}

	/*
	 * Advances the internal state of the iterator (currentValueInds) to the next value.
	 */
	private void incrementCurrentValueInds() {
		currentValueInds.set(0, currentValueInds.get(0) + 1);
		int i = 0;
		while (i < currentValueInds.size() - 1 && 
				currentValueInds.get(i) > finalValueInds.get(i)) {
			currentValueInds.set(i, 0);
			currentValueInds.set(i + 1, currentValueInds.get(i + 1) + 1);
			i++;
		}		
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}
}
