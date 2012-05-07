package com.jayantkrish.jklol.inference;

import junit.framework.TestCase;

/**
 * Unit tests for {@link MeanFieldVariational}.
 * 
 * @author jayantk
 */
public class MeanFieldVariationalTest extends TestCase {

  private MeanFieldVariational mf;
  
  public void setUp() {
    mf = new MeanFieldVariational();
  }
  
  public void testProductFactorGraph() {
    // Inference in this graph should be exact.
    InferenceTestCases.testProductFactorGraphUnconditional().runTest(mf, .000001);
  }
  
  public void testNonTreeStructuredMarginals() {
    InferenceTestCases.testNonCliqueTreeUnconditional().runTest(mf, .01);
  }
  
  public void testSoftConstraintFactorGraph() {
    InferenceTestCases.testSoftConstraintFactorGraph().printMarginals(mf);
    // InferenceTestCases.testSoftConstraintFactorGraph().runTest(mf, .01);
  }
}
