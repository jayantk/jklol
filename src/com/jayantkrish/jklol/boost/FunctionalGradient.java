package com.jayantkrish.jklol.boost;

/**
 * A functional gradient for the regression boosting reduction.
 *
 * @author jayantk
 */
public interface FunctionalGradient {

  /**
   * Adds the contents of {@code other} to this functional gradient.
   * This operation combines all of the training examples from both
   * gradients into {@code this}.
   * 
   * @param other
   */
  public void combineExamples(FunctionalGradient other);
}
