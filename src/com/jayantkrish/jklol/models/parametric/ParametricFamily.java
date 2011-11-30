package com.jayantkrish.jklol.models.parametric;

import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A family of graphical models indexed by parameters of type {@code T}. This
 * interface supports retrieving a graphical model given a set of parameters.
 * {@code ParametricFamily} is a central part of parameter estimation in this
 * library. Training algorithms (see {@link com.jayantkrish.jklol.training})
 * start with a parameterized family of models and search for parameters which
 * optimize some objective.
 * 
 * Note that this interface makes no guarantees about uniqueness; there may be
 * multiple distinct parameters which result in the same {@code FactorGraph}.
 * 
 * @param <T> type of expected parameters
 * @author jayantk
 */
public interface ParametricFamily<T> {

  /**
   * Gets the variables over which the distributions in this family are defined.
   * All {@code FactorGraph}s returned by
   * {@link #getFactorGraphFromParameters(Object)} are defined over the same
   * variables.
   * 
   * @return
   */
  public VariableNumMap getVariables();

  /**
   * Gets a {@code FactorGraph} which is the member of this family indexed by
   * {@code parameters}. Note that multiple values of {@code parameters} may
   * result in the same {@code FactorGraph}.
   * 
   * @param parameters
   * @return
   */
  public FactorGraph getFactorGraphFromParameters(T parameters);

  /**
   * Gets a new parameter vector for {@code this} with a reasonable default
   * value. Typically, the default value is the all-zero vector.
   * 
   * @return
   */
  public T getNewSufficientStatistics();

  /**
   * Accumulates sufficient statistics (in {@code statistics}) for estimating a
   * model from {@code this} family based on a point distribution at
   * {@code assignment}. {@code count} is the number of times that
   * {@code assignment} has been observed in the training data, and acts as a
   * multiplier for the computed statistics {@code assignment} must contain a
   * value for all of the variables in {@code this.getVariables()}.
   * 
   * @param statistics
   * @param assignment
   * @param count
   */
  public void incrementSufficientStatistics(SufficientStatistics statistics,
      VariableNumMap variables, Assignment assignment, double count);

  /**
   * Computes a vector of sufficient statistics for {@code this} and accumulates
   * them in {@code statistics}. The statistics are computed from the
   * (conditional) marginal distribution {@code marginals}. {@code count} is the
   * number of times {@code marginals} has been observed in the training data.
   * 
   * @param statistics
   * @param marginals
   * @param count
   */
  public void incrementSufficientStatistics(SufficientStatistics statistics,
      MarginalSet marginals, double count);
}
