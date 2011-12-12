package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Interface for parameter estimation (i.e., training).
 * 
 * @author jayantk
 */
public interface Trainer {

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
  public SufficientStatistics train(ParametricFactorGraph modelFamily,
      SufficientStatistics initialParameters,
      Iterable<Example<DynamicAssignment, DynamicAssignment>> trainingData);

  /**
   * Identical to {@link #train}, assuming that {@code modelFamily} is a
   * non-dynamic factor graph. As a result, {@code DynamicAssignment}s are
   * unnecessary.
   * 
   * @param modelFamily
   * @param initialParameters
   * @param trainingData
   * @return
   */
  public SufficientStatistics trainFixed(ParametricFactorGraph modelFamily,
      SufficientStatistics initialParameters,
      Iterable<Example<Assignment, Assignment>> trainingData);

}
