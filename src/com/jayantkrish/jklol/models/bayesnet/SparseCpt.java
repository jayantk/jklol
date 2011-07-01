package com.jayantkrish.jklol.models.bayesnet;

import java.util.Iterator;
import java.util.List;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A sparse conditional probability table (for a BN). Stores a conditional probability over a set of
 * children conditioned on a set of parents. The CPT is sparse because some outcomes are guaranteed to
 * have 0 probability.
 *
 * Also stores sufficient statistics for estimating this same CPT.
 */ 
public class SparseCpt extends Cpt {

	public SparseCpt(List<DiscreteVariable> parents, List<DiscreteVariable> children) {
		super(parents, children);
	}

	public void setNonZeroProbabilityOutcome(Assignment a) {
		if (!childStatistics.containsKey(a)) {
			setOutcomeCount(a, 0.0);
		}
	}

	public Iterator<Assignment> assignmentIterator() {
		return childStatistics.assignmentIterator();
	}
}