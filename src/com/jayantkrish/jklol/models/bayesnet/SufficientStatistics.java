package com.jayantkrish.jklol.models.bayesnet;

/**
 * The sufficient statistics of a {@code FactorGraph}. This class represents the
 * (expected) occurrence counts of a set of events (or outcomes) which can be
 * used to estimate a {@code FactorGraph}.
 * 
 * @author jayantk
 */
public interface SufficientStatistics {

  /**
   * Adds the event counts of {@code other} to {@code this}. Equivalent to
   * {@code this = this + (multiplier * other)} for each event in {@code this}.
   * .
   * 
   * @param other
   * @param multiplier
   */
  public void increment(SufficientStatistics other, double multiplier);

  /**
   * Adds a constant to each event count. This method is useful for performing
   * add-one smoothing when estimating {@link BayesNet}s.
   * 
   * @param amount
   */
  public void increment(double amount);
}
