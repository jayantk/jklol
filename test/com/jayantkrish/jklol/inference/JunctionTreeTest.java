package com.jayantkrish.jklol.inference;

import java.util.Arrays;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link JunctionTree}.
 * 
 * @author jayant
 */
public class JunctionTreeTest extends TestCase {
  
  private static final double TOLERANCE = 1e-10;

	public void testBasicMarginals() {
		InferenceTestCases.testBasicUnconditional().runTest(new JunctionTree(), TOLERANCE);
	}

	public void testNonTreeStructuredMarginals() {
		InferenceTestCases.testNonCliqueTreeUnconditional().runTest(new JunctionTree(), TOLERANCE);
	}

	public void testTriangleMarginals() {
	  InferenceTestCases.testTriangleFactorGraphMarginals().runTest(new JunctionTree(), TOLERANCE);
	}

	public void testConditionals() {
		InferenceTestCases.testBasicConditional().runTest(new JunctionTree(), 0.0);
	}

	public void testConditionalsAllVars() {
	  FactorGraph fg = InferenceTestCases.basicFactorGraph();
	  FactorGraph conditional = fg.conditional(fg.outcomeToAssignment(
	      Arrays.asList("Var0", "Var1", "Var2", "Var3", "Var4"),
	      Arrays.asList("T", "foo", "T", "T", "U")));
	  
	  JunctionTree jt = new JunctionTree();
	  MarginalSet marginals = jt.computeMarginals(conditional);
	  
	  assertEquals(1.0, marginals.getMarginal(Ints.asList()).getUnnormalizedProbability(Assignment.EMPTY));
	}
	
	public void testMaxMarginals() {
		InferenceTestCases.testBasicMaxMarginals().runTest(new JunctionTree(), 0.0);
	}
	
	public void testConditionalMaxMarginals() {
		InferenceTestCases.testConditionalMaxMarginals().runTest(new JunctionTree(), 0.0);
	}
	
	public void testTriangleMaxMarginals() {
	  InferenceTestCases.testTriangleFactorGraphMaxMarginals().runTest(new JunctionTree(), 0.0);
	}
}

