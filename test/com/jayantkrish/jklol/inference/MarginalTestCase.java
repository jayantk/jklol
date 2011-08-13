package com.jayantkrish.jklol.inference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.inference.InferenceEngine;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A MarginalTestCase tests several marginal probabilities. 
 * @author jayant
 *
 */
public class MarginalTestCase {

	private FactorGraph factorGraph;
	private Assignment condition;
	private boolean maxMarginal;
	
	private Map<Integer[], MarginalTest> variableMarginalTests;
	
	/**
	 * Create a new test case for the marginal distribution over variables. 
	 * The marginal is conditioned on the provided assignment, and maxMarginal determines
	 * whether the marginals are max marginals. 
	 */
	public MarginalTestCase(FactorGraph factorGraph, Assignment condition, boolean maxMarginal) {
		this.factorGraph = factorGraph;
		this.condition = condition;
		this.maxMarginal = maxMarginal;
		
		variableMarginalTests = Maps.newHashMap();
	}

	public void addTest(double expectedProb, Integer[] variableNums, String ... varValues) {
		if (!variableMarginalTests.containsKey(variableNums)) {
			variableMarginalTests.put(variableNums, new MarginalTest());
		}
		variableMarginalTests.get(variableNums).addTest(expectedProb, varValues);
	}
	
	public void runTest(InferenceEngine inference, double tolerance) {
		inference.setFactorGraph(factorGraph);

		MarginalSet marginals = null;
		if (maxMarginal) {
			marginals = inference.computeMaxMarginals(condition);
		} else {
			marginals = inference.computeMarginals(condition);
		}
		
		for (Map.Entry<Integer[], MarginalTest> testCase : variableMarginalTests.entrySet()) {
			DiscreteFactor marginal = (DiscreteFactor) marginals.getMarginal(Arrays.asList(testCase.getKey()));
			testCase.getValue().runTests(marginal, marginals.getPartitionFunction(), tolerance);
		}

	}
		
	private static class MarginalTest {
		private List<Double> expectedProbs;
		private List<String[]> expectedVars;

		public MarginalTest() {
			this.expectedProbs = Lists.newArrayList();
			this.expectedVars = Lists.newArrayList();
		}
		
		public void addTest(double expectedProb, String[] varValues) {
			expectedProbs.add(expectedProb);
			expectedVars.add(varValues);
		}

		public void runTests(DiscreteFactor marginal, double partitionFunction, double tolerance) {
			System.out.println(marginal + " / " + partitionFunction);
			for (int i = 0; i < expectedProbs.size(); i++) {
				double modelProbability = marginal.getUnnormalizedProbability(
						Arrays.asList(expectedVars.get(i))) / partitionFunction;
				Assert.assertTrue("Expected: <" + expectedProbs.get(i) + "> Actual: <" 
						+ modelProbability + "> tolerance " + tolerance,
						Math.abs(expectedProbs.get(i) - modelProbability) <= tolerance);  
			}
		}
	}
}
