package com.jayantkrish.jklol.models.parametric;

/**
 * The sufficient statistics of a {@code FactorGraph}. This class represents
 * (expected) occurrence counts for a set of events which can be used to
 * estimate a {@code FactorGraph}. {@code SufficientStatistics} may also
 * represent the parameter vector for a graphical model, since a model family's
 * parameters have the same dimensionality as its sufficient statistics.
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
   * Multiplies each event count in {@code this} by {@code amount}.
   * 
   * @param amount
   */
  public void multiply(double amount);

  /**
   * Increments each element of {@code this} with a random perturbation. Each
   * element is drawn independently from a mean-0 Gaussian with standard
   * deviation {@code stddev}.
   * 
   * @param variance
   */
  public void perturb(double stddev);

  /**
   * Gets the L2 norm of {@code this}, treating {@code this} as a vector. In
   * other words, this method returns the square root of the sum of the squares
   * of each entry.
   * 
   * @param exponent
   * @return
   */
  public double getL2Norm();

  /**
   * Attempts to convert {@code this} into a {@link ListSufficientStatistics}.
   * Throws {@code CoercionError} if conversion is not possible.
   * 
   * @return
   */
  public ListSufficientStatistics coerceToList();
}
