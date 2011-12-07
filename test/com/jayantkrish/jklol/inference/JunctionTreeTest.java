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

	public void testBasicMarginals() {
		InferenceTestCases.testBasicUnconditional().runTest(new JunctionTree(), 0.0);
	}
	
	public void testNonTreeStructuredMarginals() {
		InferenceTestCases.testNonCliqueTreeUnconditional().runTest(new JunctionTree(), 0.0);
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
	  
	  assertEquals(6.0, marginals.getPartitionFunction());
	  assertEquals(6.0, marginals.getMarginal(Ints.asList()).getUnnormalizedProbability(Assignment.EMPTY));
	}

	public void testMaxMarginals() {
		InferenceTestCases.testBasicMaxMarginals().runTest(new JunctionTree());
	}
	
	public void testSequence() {
	  InferenceTestCases.testSequenceUnconditional().runTest(new JunctionTree(), 0.0);
	}
	
	public void testSequenceConditional() {
	  InferenceTestCases.testSequenceConditional().runTest(new JunctionTree(), 0.0);
	}
}

