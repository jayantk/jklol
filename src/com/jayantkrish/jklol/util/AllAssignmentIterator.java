package com.jayantkrish.jklol.util;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * An iterator over all possible assignments to a set of variables.
 */
public class AllAssignmentIterator implements Iterator<Assignment> {

	private VariableNumMap vars;
	private Iterator<int[]> valueIterator;
	private List<Object> currentValues;

	/**
	 * Create an iterator over the assignments of the variables in varNumMap. varNumMap must contain
	 * only {@link DiscreteVariable}s, otherwise an exception is thrown. 
	 * @param varNumMap
	 */
	public AllAssignmentIterator(VariableNumMap varNumMap) {
		Preconditions.checkArgument(varNumMap.getDiscreteVariables().size() == varNumMap.size());
		this.vars = varNumMap;
		currentValues = Lists.newArrayList();
		for (int i = 0; i < vars.size(); i++) {
		  currentValues.add(null);
		}
		valueIterator = initializeValueIterator(varNumMap);
	}

	/*
	 * Initializes the variable values controlling the iteration position. 
	 */
	private static Iterator<int[]> initializeValueIterator(VariableNumMap vars) {
		int[] dimensionSizes = new int[vars.size()];
		
		List<DiscreteVariable> discreteVars = vars.getDiscreteVariables();
		for (int i = 0; i < discreteVars.size(); i++) {
		  dimensionSizes[i] = discreteVars.get(i).numValues();
		}
		return new IntegerArrayIterator(dimensionSizes, new int[0]);
	}

	public boolean hasNext() {
		return valueIterator.hasNext();
	}

	public Assignment next() {
	  int[] currentValue = valueIterator.next();
		return valueToAssignment(currentValue);
	}

	/*
	 * Translates currentValueInds into an assignment.
	 */
	private Assignment valueToAssignment(int[] value) {
		List<DiscreteVariable> discreteVars = vars.getDiscreteVariables();
		for (int i = 0; i < value.length; i++) {
			currentValues.set(i, discreteVars.get(i).getValue(value[i]));
		}
		return new Assignment(vars.getVariableNums(), currentValues);
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}
}
