package com.jayantkrish.jklol.inference;

import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
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

  private final VariableNumMap maxMarginalVariables;
  private final List<Double> expectedProbs;
  private final List<String[]> assignments;

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
      Assignment bestAssignment, VariableNumMap maxMarginalVariables) {
    this.factorGraph = factorGraph;
    this.condition = condition;
    this.bestAssignment = bestAssignment;
    this.maxMarginalVariables = maxMarginalVariables;

    this.expectedProbs = Lists.newArrayList();
    this.assignments = Lists.newArrayList();
  }

  public void addTest(String[] assignment, double prob) {
    this.assignments.add(assignment);
    this.expectedProbs.add(prob);
  }

  /**
   * Runs this test case on {@code marginalCalculator}, raising JUnit assertion
   * errors on failure.
   * 
   * @param marginalCalculator
   */
  public void runTest(MarginalCalculator marginalCalculator, double tolerance) {
    FactorGraph conditionalFactorGraph = factorGraph.conditional(condition);
    MaxMarginalSet maxMarginals = marginalCalculator.computeMaxMarginals(conditionalFactorGraph);
    Assert.assertEquals(1, maxMarginals.beamSize());
    Assert.assertEquals(bestAssignment, maxMarginals.getNthBestAssignment(0));

    // Try getting an equivalent assignment using getBestAssignmentGiven.
    MaxMarginalSet unconditionalMaxMarginals = marginalCalculator.computeMaxMarginals(factorGraph);
    Assignment bestPrediction = unconditionalMaxMarginals.getNthBestAssignment(0, condition);
    Assert.assertEquals(bestAssignment, bestPrediction);

    Factor factor = maxMarginals.getMaxMarginal(maxMarginalVariables);
    for (int i = 0; i < expectedProbs.size(); i++) {
      double prob = factor.getUnnormalizedProbability(Arrays.asList(assignments.get(i)));
      Assert.assertTrue("Expected: <" + expectedProbs.get(i) + "> Actual: <"
          + prob + "> tolerance: " + tolerance, Math.abs(prob - expectedProbs.get(i)) <= tolerance);
    }
  }

  /**
   * Runs a test that only checks the best assignment against the expected best
   * assignment.
   * 
   * @param marginalCalculator
   */
  public void runAssignmentTest(MarginalCalculator marginalCalculator) {
    FactorGraph conditionalFactorGraph = factorGraph.conditional(condition);
    MaxMarginalSet maxMarginals = marginalCalculator.computeMaxMarginals(conditionalFactorGraph);
    Assert.assertEquals(1, maxMarginals.beamSize());
    Assert.assertEquals(bestAssignment, maxMarginals.getNthBestAssignment(0));
  }
}
