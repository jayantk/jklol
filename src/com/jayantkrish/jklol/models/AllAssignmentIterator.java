package com.jayantkrish.jklol.models;

import java.util.*;

/**
 * An iterator over all possible assignments to a set of variables.
 */
public class AllAssignmentIterator implements Iterator<Assignment> {
    
    private List<Variable> vars;
    private List<Integer> varNums;
    private List<Integer> currentValues;
    private List<Integer> finalValues;
    
    public AllAssignmentIterator(List<Integer> varNums, List<Variable> vars) {
	this.vars = vars;
	this.varNums = varNums;
	this.currentValues = new ArrayList<Integer>(vars.size());
	this.finalValues = new ArrayList<Integer>(vars.size());
	for (int i = 0; i < vars.size(); i++) {
	    currentValues.add(0);
	    finalValues.add(vars.get(i).numValues() - 1);
	}
	// Set the last index to one higher than the actual number of values; when we increment
	// currentValues to this point, we will be done.
	finalValues.set(vars.size() - 1, finalValues.get(vars.size() - 1) + 1);
    }
    
    public boolean hasNext() {
	return !(currentValues.get(vars.size() - 1).equals(finalValues.get(vars.size() - 1)));
    }
    
    public Assignment next() {
	Assignment a = new Assignment(varNums, currentValues);
	currentValues.set(0, currentValues.get(0) + 1);
	int i = 0;
	while (i < currentValues.size() - 1 && 
		currentValues.get(i) > finalValues.get(i)) {
	    currentValues.set(i, 0);
	    currentValues.set(i + 1, currentValues.get(i + 1) + 1);
	    i++;
	}
	return a;
    }
    
    public void remove() {
	throw new UnsupportedOperationException();
    }
    
}
