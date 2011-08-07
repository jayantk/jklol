package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.util.Assignment;

public abstract class AbstractInferenceEngine implements InferenceEngine {

	@Override
	public MarginalSet computeMarginals() {
		return computeMarginals(Assignment.EMPTY);
	}
	
	@Override
	public MarginalSet computeMaxMarginals() {
		return computeMaxMarginals(Assignment.EMPTY);
	}
}
