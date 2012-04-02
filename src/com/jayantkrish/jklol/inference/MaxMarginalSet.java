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
   * The returned assignment is guaranteed to have positive probability. If
   * fewer than {@code n} such assignments exist, a {@code ZeroProbabilityError}
   * is thrown. This exception is unchecked because most graphical models will
   * not have this problem. However, the exception may be caught and handled --
   * for example, by skipping the example during training.
   * 
   * @param n
   * @return
   */
  Assignment getNthBestAssignment(int n);
}
