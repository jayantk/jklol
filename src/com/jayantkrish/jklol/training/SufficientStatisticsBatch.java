package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * The aggregate sufficient statistics for a collection of examples. This class
 * is used for batch computation of sufficient statistics, see
 * {@link SufficientStatisticsMapper} and {@link SufficientStatisticsReducer}.
 * 
 * @author jayantk
 */
public class SufficientStatisticsBatch {

  private SufficientStatistics statistics;
  private double loglikelihood;
  private int numExamples;

  public SufficientStatisticsBatch(SufficientStatistics statistics, 
      double loglikelihood, int numExamples) {
    this.statistics = statistics;
    this.loglikelihood = loglikelihood;
    this.numExamples = numExamples;
  }

  public SufficientStatistics getStatistics() {
    return statistics;
  }

  /**
   * Gets the sum of the unnormalized loglikelihood of each training example in
   * the batch. The actual loglikelihood of the training data is equal to
   * {@code this.getLoglikelihood() - this.getNumExamples() * logPartitionFunction}
   * , where {@code logPartitionFunction} is the log partition function of the
   * factor graph the statistics were computed on.
   * 
   * @return
   */
  public double getLoglikelihood() {
    return loglikelihood;
  }

  public int getNumExamples() {
    return numExamples;
  }

  /**
   * Adds the statistics in {@code other} to {@code this}.
   * 
   * @param other
   */
  public void increment(SufficientStatisticsBatch other) {
    statistics.increment(other.statistics, 1.0);
    loglikelihood += other.loglikelihood;
    numExamples += other.numExamples;
  }
}
