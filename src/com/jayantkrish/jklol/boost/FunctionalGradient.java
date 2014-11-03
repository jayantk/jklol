package com.jayantkrish.jklol.boost;

/**
 * A functional gradient for the regression boosting reduction.
 * Generally, functional gradients store a loss function 
 * gradient per example, which can be used to train a 
 * regression function (see {@link BoostingFactorFamily}). 
 * However, implementations may store less information depending 
 * on the training data requirements of the particular family. 
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
