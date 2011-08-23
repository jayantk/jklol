package com.jayantkrish.jklol.models;

import java.util.Iterator;
import java.util.Set;

import com.jayantkrish.jklol.util.Assignment;

/**
 * Wraps another factor f and returns its inverse, i.e., the factor g such that f(x) * g(x) = 1 for
 * all outcomes x. There's one exception: if f assigns zero probability to some outcomes, g also
 * assigns these outcomes zero probability.
 */ 
public class InverseFactor extends DiscreteFactor {

	private DiscreteFactor baseFactor;

	public InverseFactor(DiscreteFactor f) {
		super(f.getVars());
		baseFactor = f;
	}

	public Iterator<Assignment> outcomeIterator() {
		return baseFactor.outcomeIterator();
	}

	public double getUnnormalizedProbability(Assignment assignment) {
		if (baseFactor.getUnnormalizedProbability(assignment) != 0.0) {
			return 1.0 / baseFactor.getUnnormalizedProbability(assignment);
		} else {
			return 0.0;
		}
	}

	public Set<Assignment> getAssignmentsWithEntry(int varNum, Set<Object> varValues) {
		return baseFactor.getAssignmentsWithEntry(varNum, varValues);
	}
}