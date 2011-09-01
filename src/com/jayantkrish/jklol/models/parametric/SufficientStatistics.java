package com.jayantkrish.jklol.models.parametric;

import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.models.loglinear.FeatureSufficientStatistics;

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
   * add-one smoothing when estimating {@link ParametricFactorGraph}s.
   * 
   * @param amount
   */
  public void increment(double amount);

  /**
   * Attempts to convert {@code this} into a {@link Cpt}. Throws
   * {@code CoercionError} if conversion is not possible.
   * 
   * @return
   */
  public Cpt coerceToCpt();
  
  /**
   * Attempts to convert {@code this} into a {@link FeatureSufficientStatistics}.
   * @return
   */
  public FeatureSufficientStatistics coerceToFeature();

  /**
   * Attempts to convert {@code this} into a {@link ListSufficientStatistics}.
   * Throws {@code CoercionError} if conversion is not possible.
   * 
   * @return
   */
  public ListSufficientStatistics coerceToList();
}
