package com.jayantkrish.jklol.inference;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * StructuredVariationalInference performs variational inference with respect to a
 * structured graph (which subsumes mean field). 
 * @author jayant
 *
 */
public class StructuredVariationalInference implements InferenceEngine {

	private FactorGraph factorGraph;
	private InferenceEngine baseEngine;
	private Function<FactorGraph, List<StructuredComponent>> simpleModelConstructor;

	private List<StructuredComponent> structuredComponents;
	private List<MarginalSet> approximateMarginals;
	
	public StructuredVariationalInference(Function<FactorGraph, FactorGraph> simpleModelConstructor) {
		this.simpleModelConstructor = simpleModelConstructor;
		factorGraph = null;
	}
		
	@Override
	public void computeMarginals() {
		
		for (int i = 0; i < structuredComponents.size(); i++) {
			StructuredComponent currentCluster = structuredComponents.get(i);

			FactorGraph conditionedApproximation = currentCluster.getFactorGraphFromConditionals(approximateMarginals);

			baseEngine.setFactorGraph(conditionedApproximation);
			approximateMarginals.set(i, baseEngine.computeMarginals());
		}
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
	public void setFactorGraph(FactorGraph f) {
		this.factorGraph = f;
		this.structuredComponents = simpleModelConstructor.apply(f); 
	}

	private class StructuredComponent {

		private FactorGraph factorGraph;
		private Map<Integer, VariableNumMap> factorVarsToCondition;
		
		public StructuredComponent(FactorGraph factorGraph, VariableNumMap varsInComponent) {
			this.factorGraph = factorGraph;
			this.factorVarsToCondition = Maps.newHashMap();
			
			for (int i = 0; i < factorGraph.numFactors(); i++) {
				Factor factor = factorGraph.getFactorFromIndex(i);
				factorVarsToCondition.put(i, factor.getVars().removeAll(varsInComponent));
			}
		}
		
		public FactorGraph getFactorGraphFromConditionals(MarginalSet marginals) {
			FactorGraph returnGraph = new FactorGraph(factorGraph);
			for (int i = 0; i < factorGraph.numFactors(); i++) {
				Factor factor = factorGraph.getFactorFromIndex(i);
				Factor conditionedValues = marginals.getMarginal(factorVarsToCondition.get(i));
				returnGraph.setFactor(i, factor.conditional(conditionedValues));
			}
			return returnGraph;
		}
	}
	
	/*
	 * Composes a set of marginal distributions over disjoint sets of variables into a 
	 * single set of marginals
	 */
	private class DisjointUnionMarginalSet implements MarginalSet {
		
	}
}
