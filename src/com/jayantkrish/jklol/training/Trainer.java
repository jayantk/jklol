package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * Interface for parameter estimation (i.e., training).
 * 
 * @author jayantk
 */
public interface Trainer<T, E> {

  /**
   * Estimates parameters for {@code modelFamily} using {@code trainingData}.
   * {@code trainingData} contains the observed input/output pairs for training;
   * the input and output of each training example should be disjoint. Hidden
   * variables (which are unobserved in both the input and output) are allowed,
   * though may be unsupported by some {@code Trainer}s. {@code trainingData} is
   * assumed to be pre-shuffled into a random order.
   * 
   * {@code initialParameters} are used as the starting point for the
   * optimization procedure. This selection is only relevant when the training
   * procedure finds a local minimum -- this typically occurs when hidden
   * variables are used. {@code initialParameters} may be mutated by this
   * method.
   * 
   * @param modelFamily
   * @param initialParameters
   * @param trainingData
   * @return
   */
  public SufficientStatistics train(T modelFamily,
      SufficientStatistics initialParameters,
      Iterable<E> trainingData);
}
