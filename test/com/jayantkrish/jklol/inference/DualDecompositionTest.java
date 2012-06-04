package com.jayantkrish.jklol.inference;

import junit.framework.TestCase;

/**
 * Unit tests for {@link DualDecomposition}.
 * 
 * @author jayantk
 */
public class DualDecompositionTest extends TestCase {

  public void testMaxMarginals() {
    InferenceTestCases.testBasicMaxMarginals().runAssignmentTest(new DualDecomposition(100));
  }
 
	public void testConditionalMaxMarginals() {
		InferenceTestCases.testConditionalMaxMarginals().runAssignmentTest(new DualDecomposition(100));
	}
} 
