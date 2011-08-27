package com.jayantkrish.jklol.models.bayesnet;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A factor that is parameterized by one or more conditional probability tables
 * (CPT)s.
 * 
 * <p>
 * Since CPTs can be trivially estimated from sufficient statistics for the
 * factor, {@code CptFactor}s directly use their sufficient statistics as their
 * parameters. Hence, methods like {@link #getCurrentParameters())} return
 * {@code SufficientStatistics}.
 * 
 * @author jayant
 */
public interface CptFactor extends Factor {

  /**
   * Gets a new vector of sufficient statistics for {@code this} factor. The
   * returned vector can be an argument to methods of this instance which take a
   * {@code SufficientStatistics} argument, e.g.,
   * {@link #setCurrentParameters(SufficientStatistics)}. The returned vector
   * has zero counts for all outcomes of interest.
   * 
   * @return
   */
  public abstract SufficientStatistics getNewSufficientStatistics();

  /**
   * Computes sufficient statistics for {@code this} factor based on an assumed
   * point distribution at {@code assignment}. {@count} is the number of times
   * that {@code assignment} has been observed. The returned sufficient
   * statistics are {@code count} for each outcome that occurs in
   * {@code assignment}, and 0 for all other outcomes.
   * 
   * @param marginal
   * @param count
   * @return
   */
  public abstract SufficientStatistics getSufficientStatisticsFromAssignment(
      Assignment assignment, double count);

  /**
   * Computes sufficient statistics for {@code this} factor from the marginal
   * distribution {@code marginal}. The returned sufficient statistics summarize
   * the probabilities of the outcomes of interest in {@code marginal}.
   * 
   * @param marginal
   * @param count
   * @param partitionFunction
   * @return
   */
  public abstract SufficientStatistics getSufficientStatisticsFromMarginal(Factor marginal,
      double count, double partitionFunction);

  /**
   * Gets the current parameters of this factor as a vector of sufficient
   * statistics. Note that there may be many {@code SufficientStatistics}
   * vectors which result in the same probability distribution.
   * 
   * @return
   */
  public abstract SufficientStatistics getCurrentParameters();

  /**
   * Sets the parameters of this factor based on {@code statistics}.
   * 
   * @param cpt
   */
  public abstract void setCurrentParameters(SufficientStatistics statistics);
}