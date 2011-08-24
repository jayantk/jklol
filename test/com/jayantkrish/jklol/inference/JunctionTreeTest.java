package com.jayantkrish.jklol.inference;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.JunctionTree;

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

	public void testMaxMarginals() {
		InferenceTestCases.testBasicMaxMarginals().runTest(new JunctionTree());
	}
}

