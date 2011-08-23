package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.util.Assignment;

public abstract class AbstractMarginalCalculator implements MarginalCalculator {

	@Override
	public MarginalSet computeMarginals() {
		return computeMarginals(Assignment.EMPTY);
	}
	
	@Override
	public MaxMarginalSet computeMaxMarginals() {
		return computeMaxMarginals(Assignment.EMPTY);
	}
}
