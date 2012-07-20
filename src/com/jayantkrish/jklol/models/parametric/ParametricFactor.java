package com.jayantkrish.jklol.models.parametric;

import java.io.Serializable;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A family of {@link Factor}s indexed by parameters of
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
public interface ParametricFactor extends Serializable {

  /**
   * Gets the variables over which this {@code ParametricFactor} is defined. The
   * returned variables are the same as the variables for the factor returned by
   * {@link #getFactorFromParameters(Object)}.
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
  public Factor getFactorFromParameters(SufficientStatistics parameters);

  /**
   * Gets a human-interpretable string describing {@code parameters}. This
   * method returns one line per parameter, containing a description of the
   * parameter and its value.
   * 
   * @param parameters
   * @return
   */
  public String getParameterDescription(SufficientStatistics parameters);
  
  
  /**
   * Gets a human-interpretable XML description {@code parameters}. 
   * 
   * @param parameters
   * @return
   */
  public String getParameterDescriptionXML(SufficientStatistics parameters);

  /**
   * Gets a new all-zero vector of parameters for {@code this}. The returned
   * vector can be an argument to methods of this instance which take parameters
   * as an argument, e.g., {@link #getFactorFromParameters()}.
   * 
   * @return
   */
  public SufficientStatistics getNewSufficientStatistics();

  /**
   * Computes sufficient statistics for {@code this} factor based on an assumed
   * point distribution at {@code assignment}, and increments
   * {@code sufficientStatistics} with the statistics. {@count} is the number of
   * times that {@code assignment} has been observed.
   * 
   * @param statistics
   * @param assignment
   * @param count
   * @return
   */
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      Assignment assignment, double count);

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
   * @param marginal
   * @param conditionalAssignment
   * @param count
   * @param partitionFunction
   * @return
   */
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction);

  /**
   * Gets a serialized version of this {@code ParametricFactor}. Typically, this
   * method should be invoked via {@link ParametricFactorGraph#toProto}.
   * <p>
   * {@code variableTypeIndex} is a mapping from variables to unique integer
   * indexes that must be serialized independently and is required to
   * deserialize the returned protocol buffer. This method may add variables to
   * {@code variableTypeIndex}, but may not alter the list in any other way.
   * 
   * @param variableTypeIndex
   * @return
   */
  public ParametricFactorProto toProto(IndexedList<Variable> variableTypeIndex);
}
