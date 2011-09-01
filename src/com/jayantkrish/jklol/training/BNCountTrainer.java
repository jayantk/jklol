package com.jayantkrish.jklol.training;

import java.util.List;

import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Trains a {@link #ParametricFactorGraph} using empirical outcome counts from a data set.
 */
public class BNCountTrainer {

  public BNCountTrainer() {
  }

  /**
   * Selects a model from the set of {@code bn} by counting the outcome
   * occurrences in {@code trainingData} and constructing a parameter vector
   * from the result.
   * 
   * <p>
   * Note that the returned estimates are unsmoothed; to get smoothed estimates,
   * use {@link SufficientStatistics#increment(double)} on the result.
   * 
   * @param bn
   * @param trainingData
   */
  public SufficientStatistics train(ParametricFactorGraph bn, List<Assignment> trainingData) {
    // For each training example, increment sufficient statistics appropriately.
    SufficientStatistics accumulatedStats = bn.getNewSufficientStatistics();
    for (Assignment assignment : trainingData) {
      SufficientStatistics assignmentStats = bn.computeSufficientStatistics(assignment, 1.0);
      accumulatedStats.increment(assignmentStats, 1.0);
    }
    return accumulatedStats;
  }
}