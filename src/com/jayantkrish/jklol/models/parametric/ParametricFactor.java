package com.jayantkrish.jklol.models.parametric;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A set of {@link Factor}s indexed by a parameter of type {@code T}. A
 * {@code ParametricFactor} is not a {@code Factor}, but rather an entire class
 * of {@code Factor}s. This interface is typically used by a
 * {@link ParametricFamily} to construct the individual clique distributions
 * which make up the {@code FactorGraph}.
 * 
 * This interface supports common operations on parameterized distributions,
 * including constructing {@code Factor}s from parameters and estimating
 * sufficient statistics for the parameters given a distribution. The type of
 * the sufficient statistics is equivalent to the type of parameter; the reason
 * for this equivalence is that in exponential families, both of these
 * quantities are vectors with the same dimensionality.
 * 
 * For an example of a {@code ParametricFactor}, see {@link CptTableFactor}.
 * 
 * @author jayantk
 * @param <T>
 */
public interface ParametricFactor<T> {

  /**
   * Gets the variables over which this {@code ParametricFactor} is defined. The
   * returned variables are the same as the variables for the factor returned by
   * {@link #getFactorFromParameters(Object)}.
   * 
   * @return
   */
  public VariableNumMap getVars();

  /**
   * Gets a {@code Factor} from a set of {@code parameters}. This method selects
   * an element of this set of {@code Factor}s. Note that multiple values of
   * {@code parameters} may return the same {@code Factor}, that is, there is no
   * guarantee of uniqueness.
   * 
   * @param parameters
   * @return
   */
  public Factor getFactorFromParameters(T parameters);

  /**
   * Gets a new vector of parameters for {@code this} with a reasonable default
   * value. A typical default is the all-zero vector. The returned vector can be
   * an argument to methods of this instance which take parameters as an
   * argument, e.g., {@link #getFactorFromParameters()}.
   * 
   * @return
   */
  public T getNewSufficientStatistics();

  /**
   * Computes sufficient statistics for {@code this} factor based on an assumed
   * point distribution at {@code assignment}. {@count} is the number of times
   * that {@code assignment} has been observed. The returned sufficient
   * statistics are {@code count} for each outcome that occurs in
   * {@code assignment}, and 0 for all other outcomes.
   * 
   * @param assignment
   * @param count
   * @return
   */
  public abstract T getSufficientStatisticsFromAssignment(Assignment assignment, double count);

  /**
   * Computes sufficient statistics for {@code this} factor from the marginal
   * distribution {@code marginal}. The returned sufficient statistics summarize
   * the probabilities of the outcomes of interest in {@code marginal}.
   * {@code marginal} is a distribution over the same variables as
   * {@code this.getVars()}.
   * 
   * @param marginal
   * @param count
   * @param partitionFunction
   * @return
   */
  public T getSufficientStatisticsFromMarginal(Factor marginal,
      double count, double partitionFunction);
}
