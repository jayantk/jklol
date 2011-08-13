package com.jayantkrish.jklol.inference;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.GibbsSampler;

/**
 * Tests for GibbsSampler
 * @author jayant
 *
 */
public class GibbsSamplerTest extends TestCase {

	// At the moment, the GibbsSampler doesn't support 0 probability assignments.
	/*
	public void testBasicMarginals() {
		InferenceTestCase.testBasicUnconditional().runTest(new GibbsSampler(1000, 1000, 1), 0.05);
	}
	
	public void testConditionals() {
		InferenceTestCase.testBasicConditional().runTest(new GibbsSampler(1000, 1000, 1), 0.05);
	}
	*/
	
	public void testNonTreeStructuredMarginals() {
		InferenceTestCase.testNonCliqueTreeUnconditional().runTest(new GibbsSampler(1000, 1000, 1), 0.05);
	}
}
