package com.jayantkrish.jklol.inference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A MarginalTestCase tests several marginal probabilities. 
 *
 * @author jayant
 */
public class MarginalTestCase {

	private FactorGraph factorGraph;
	private Assignment condition;
	
	private Map<String[], MarginalTest> variableMarginalTests;
	
	/**
	 * Create a new test case for the marginal distribution over variables. 
	 * The marginal is conditioned on the provided assignment, and maxMarginal determines
	 * whether the marginals are max marginals. 
	 */
	public MarginalTestCase(FactorGraph factorGraph, Assignment condition) {
		this.factorGraph = factorGraph;
		this.condition = condition;

		variableMarginalTests = Maps.newHashMap();
	}

	public void addTest(double expectedProb, String[] variableNums, String ... varValues) {
		if (!variableMarginalTests.containsKey(variableNums)) {
			variableMarginalTests.put(variableNums, new MarginalTest());
		}
		variableMarginalTests.get(variableNums).addTest(expectedProb, varValues);
	}
	
	public void runTest(MarginalCalculator inference, double tolerance) {
	  FactorGraph conditionedFactorGraph = factorGraph.conditional(condition);
	  MarginalSet marginals = inference.computeMarginals(conditionedFactorGraph);
	  
	  Assert.assertEquals(condition, marginals.getConditionedValues().intersection(condition.getVariableNums()));
	  Assert.assertTrue(marginals.getVariables().containsAll(
	      marginals.getConditionedValues().getVariableNums()));

	  for (Map.Entry<String[], MarginalTest> testCase : variableMarginalTests.entrySet()) {
	    VariableNumMap variables = marginals.getVariables().getVariablesByName(testCase.getKey());
	    DiscreteFactor marginal = (DiscreteFactor) marginals.getMarginal(variables.getVariableNums());
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
