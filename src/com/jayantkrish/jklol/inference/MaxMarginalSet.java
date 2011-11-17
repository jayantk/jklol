package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.util.Assignment;

/**
 * Max marginals, or maximal probability assignments, for a graphical model.
 * This class supports retrieving the top-{@code #beamSize()} maximal
 * probability assignments, in order of probability.
 * 
 * {@code MaxMarginalSet} is immutable.
 * 
 * @author jayant
 */
public interface MaxMarginalSet {

  /**
   * Returns a {@code MaxMarginalSet} identical to this one where {@code values}
   * is automatically unioned with each returned assignment. The variables in
   * {@code values} must be disjoint with the variables in each assignment of
   * {@code this}.
   * 
   * @param values
   * @return
   */
  MaxMarginalSet addConditionalVariables(Assignment values);

  /**
   * Gets the number of maximum probability assignments contained in
   * {@code this}.
   * 
   * @return
   */
  int beamSize();

  /**
   * Gets the {@code n}th most probable assignment. Assignments are zero
   * indexed, and therefore {@code n} must be less than {@link #beamSize()}.
   * 
   * @param n
   * @return
   */
  Assignment getNthBestAssignment(int n);
}
