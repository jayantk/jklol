package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.util.Assignment;

public abstract class AbstractInferenceEngine implements InferenceEngine {

	@Override
	public void computeMarginals() {
		computeMarginals(Assignment.EMPTY);
	}

	@Override
	public void computeMaxMarginals() {
		computeMaxMarginals(Assignment.EMPTY);
	}
}
