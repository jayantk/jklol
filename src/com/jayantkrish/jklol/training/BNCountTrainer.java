package com.jayantkrish.jklol.training;

import java.util.List;

import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Trains a Bayes Net using empirical CPT counts.
 */
public class BNCountTrainer {

  public BNCountTrainer() {
  }

  /**
   * Trains {@code bn} by counting the outcome occurrences in
   * {@code trainingData}. Note that the resulting estimates are unsmoothed; for
   * smoothed estimates, use {@code bn.getCurrentParameters().increment(amount)}
   * .
   * 
   * @param bn
   * @param trainingData
   */
  public void train(BayesNet bn, List<Assignment> trainingData) {
    // For each training example, increment sufficient statistics appropriately.
    for (Assignment assignment : trainingData) {
      SufficientStatistics assignmentStats = bn.computeSufficientStatistics(assignment, 1.0);
      bn.getCurrentParameters().increment(assignmentStats, 1.0);
    }
  }
}