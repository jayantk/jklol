package com.jayantkrish.jklol.inference;

import junit.framework.Assert;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A test case for an algorithm that computes max marginals.
 * 
 * @author jayantk
 */
public class MaxMarginalTestCase {

  private final FactorGraph factorGraph;
  private final Assignment condition;
  private final Assignment bestAssignment;

  /**
   * Creates a test case which computes max marginals of {@code factorGraph},
   * conditioned on {@code condition}. The expected most probable assignment is
   * {@code bestAssignment}.
   * 
   * @param factorGraph
   * @param condition
   * @param bestAssignment
   */
  public MaxMarginalTestCase(FactorGraph factorGraph, Assignment condition,
      Assignment bestAssignment) {
    this.factorGraph = factorGraph;
    this.condition = condition;
    this.bestAssignment = bestAssignment;
  }

  /**
   * Runs this test case on {@code marginalCalculator}, raising JUnit assertion
   * errors on failure.
   * 
   * @param marginalCalculator
   */
  public void runTest(MarginalCalculator marginalCalculator) {
    FactorGraph conditionalFactorGraph = factorGraph.conditional(condition);
    MaxMarginalSet maxMarginals = marginalCalculator.computeMaxMarginals(conditionalFactorGraph);
    Assert.assertEquals(1, maxMarginals.beamSize());
    Assert.assertEquals(bestAssignment, maxMarginals.getNthBestAssignment(0));
  }
}
