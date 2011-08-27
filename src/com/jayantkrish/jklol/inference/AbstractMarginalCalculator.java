package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

public abstract class AbstractMarginalCalculator implements MarginalCalculator {

	@Override
	public MarginalSet computeMarginals(FactorGraph factorGraph) {
		return computeMarginals(factorGraph, Assignment.EMPTY);
	}
	
	@Override
	public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph) {
		return computeMaxMarginals(factorGraph, Assignment.EMPTY);
	}
}
