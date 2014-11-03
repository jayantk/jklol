package com.jayantkrish.jklol.models.parametric;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A family of {@link Factor}s indexed by parameters represented as
 * {@code SufficientStatistics}. A {@code ParametricFactor} is not a factor, but
 * rather an entire class of {@code Factor}s. This interface is used by a
 * {@link ParametricFactorGraph} to construct the individual clique
 * distributions which make up the {@code FactorGraph}.
 * <p>
 * This interface supports common operations on parameterized distributions,
 * including constructing {@code Factor}s from parameters and estimating
 * sufficient statistics given a distribution. The type of the sufficient
 * statistics is equivalent to the type of parameter; the reason for this
 * equivalence is that in exponential families, both of these quantities are
 * vectors with the same dimensionality.
 * <p>
 * For an example of a {@code ParametricFactor}, see {@link CptTableFactor}.
 * 
 * @author jayantk
 */
public interface ParametricFactor extends ParametricFamily<Factor> {

  /**
   * Gets the variables over which this {@code ParametricFactor} is defined. The
   * returned variables are the same as the variables for the factor returned by
   * {@link #getModelFromParameters(Object)}.
   * 
   * @return
   */
  public VariableNumMap getVars();

  /**
   * Gets a {@code Factor} from a set of {@code parameters}. This method returns
   * an element of this parametric family. Note that multiple values of
   * {@code parameters} may return the same factor; that is, there is no
   * guarantee of uniqueness.
   * 
   * @param parameters
   * @return
   */
  public Factor getModelFromParameters(SufficientStatistics parameters);

  /**
   * Computes sufficient statistics for {@code this} factor based on an assumed
   * point distribution at {@code assignment}, and increments
   * {@code statistics} with the statistics. {@count} is the number of
   * times that {@code assignment} has been observed.
   * <p>
   * {@code assignment} must contain all of the variables in this factor,
   * and may contain additional assignments.
   * 
   * @param statistics
   * @param currentParameters
   * @param assignment
   * @param count
   * @return
   */
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Assignment assignment, double count);

  /**
   * Increments {@code statistics} by {@code count} for each assignment to {@code this} that is a
   * superset of {@code partialAssignment}. If {@code partialAssignment} contains all of the
   * variables in {@code this}, then this method is equivalent to {@link
   * #incrementSufficientStatisticsFromAssignment}.
   * 
   * @param statistics
   * @param currentParameters
   * @param partialAssignment
   * @param count
   * @return
   */
  public void incrementSufficientStatisticsFromPartialAssignment(SufficientStatistics statistics, 
      SufficientStatistics currentParameters, Assignment partialAssignment, double count);
  
  /**
   * Computes sufficient statistics for {@code this} factor from the marginal
   * distribution {@code marginal} and accumulates them in {@code statistics}.
   * {@code marginal} is a conditional distribution given
   * {@code conditionalAssignment}. The computed sufficient statistics summarize
   * the probabilities of the events of interest in {@code marginal}.
   * <p>
   * This method requires {@code marginal} to be a distribution over a subset of
   * {@code this.getVars()}. {@code marginal} and {@code conditionalAssignment}
   * must be define a disjoint partition of {@code this.getVars()}.
   * {@code count} is the number of times this marginal has been observed, and
   * {@code partitionFunction} is the normalizing constant for {@code marginal}.
   * 
   * @param statistics
   * @param currentParameters
   * @param marginal
   * @param conditionalAssignment
   * @param count
   * @param partitionFunction
   * @return
   */
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
       SufficientStatistics currentParameters, Factor marginal, Assignment conditionalAssignment,
       double count, double partitionFunction);
}
