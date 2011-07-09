package com.jayantkrish.jklol.inference;

import java.util.List;

import com.google.common.base.Function;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * StructuredVariationalInference performs variational inference with respect to a
 * structured graph (which subsumes mean field). 
 * @author jayant
 *
 */
public class StructuredVariationalInference implements InferenceEngine {

	private Function<FactorGraph, FactorGraph> simpleModelConstructor;
	private FactorGraph factorGraph;
	private FactorGraph structuredApproximation;
	
	public StructuredVariationalInference(Function<FactorGraph, FactorGraph> simpleModelConstructor) {
		this.simpleModelConstructor = simpleModelConstructor;
		factorGraph = null;
	}
	
	@Override
	public void computeMarginals() {
		// TODO Auto-generated method stub
	}

	@Override
	public void computeMarginals(Assignment assignment) {
		// TODO Auto-generated method stub

	}

	@Override
	public void computeMaxMarginals() {
		// TODO Auto-generated method stub

	}

	@Override
	public void computeMaxMarginals(Assignment assignment) {
		// TODO Auto-generated method stub

	}

	@Override
	public Factor getMarginal(List<Integer> varNums) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setFactorGraph(FactorGraph f) {
		this.factorGraph = f;
		this.structuredApproximation = simpleModelConstructor.apply(f); 
	}

}
