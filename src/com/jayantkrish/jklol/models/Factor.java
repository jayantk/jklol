package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An unnormalized probability density over a set of variables. Factors support
 * getting the density associated with {@link Assignment}s to the variables in
 * the factor. Factors additionally support common mathematical operations, such
 * as multiplication, marginalization and sampling. These operations do not
 * modify this factor. The main use of {@code Factor}s is as components of
 * {@link FactorGraph}s to represent graphical models.
 * <p>
 * For computational or mathematical tractability, the mathematical operations
 * on {@code Factor}s may only be partially supported. For example, the
 * {@link #marginalize(Collection)} operation may only support marginalizing out
 * certain subsets of variables. The {@link #product(Factor)} operation may only
 * be defined for certain subsets of the variables (which are the valid
 * separator sets for the factor). Limitations on these methods should be
 * clearly documented when they exist.
 * 
 * @author jayant
 */
public interface Factor {

  /**
   * Gets the set of variables which this factor is defined over.
   * 
   * @return
   */
  public VariableNumMap getVars();

  /**
   * Gets the unnormalized probability of a particular assignment to the
   * variables in this factor. {@code assignment} must contain all of the
   * variables in this {@code Factor}. If {@code assignment} contains a superset
   * of the variables in the factor, this method ignores the values of variables
   * which are not part of this factor.
   * 
   * @param assignment
   * @return
   */
  public double getUnnormalizedProbability(Assignment assignment);

  /**
   * Convenience method for getting the probability of an assignment. {@code
   * outcome} contains the assignment to the variables in this factor, sorted in
   * numerical order by their variable number. See
   * {@link #getUnnormalizedProbability(Assignment)}
   */
  public double getUnnormalizedProbability(List<? extends Object> outcome);

  /**
   * Convenience method for getting the probability of an assignment. {@code
   * outcome} is the assignment to the variables in this factor, sorted in
   * numerical order by their variable number. See
   * {@link #getUnnormalizedProbability(Assignment)}.
   */
  public double getUnnormalizedProbability(Object... outcome);

  /**
   * Gets the set of possible outbound messages which can be computed when this
   * {@code Factor} has received {@code inboundMessages} from other factors. The
   * keys of {@code inboundMessages} are all of the separator sets connecting
   * this factor to adjacent factors; the value for each key is the message sent
   * along that separator set to this {@code Factor}. If no message has been
   * sent on a separator set, the corresponding entry in {@code inboundMessages}
   * should be {@code null}. However, the separator set must still be provided
   * as a key in {@code inboundMessages}.
   * 
   * @param inboundMessages
   * @return A {@code Set} of the separator sets which this factor can send
   * messages on. These messages are computed by calling
   * {@link #product(Factor)} followed by {@link #marginalize(Collection)}. The
   * elements of this set are a subset of the keys of {@code inboundMessages}.
   */
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages);

  /**
   * Gets a new factor which conditions on the variables in {@code assignment}.
   * The returned factor is defined over the same variables as the original, but
   * assigns zero probability to assignments which are not supersets of {@code
   * assignment}. That is, the returned {@code Factor} returns the same
   * probability as this factor for assignments which contain the same
   * variable/value assignments as {@code assignment}, and 0 for all other
   * assignments.
   */
  public Factor conditional(Assignment assignment);

  /**
   * Same as {@link #marginalize(Collection)}.
   * 
   * @param varNumsToEliminate
   * @return
   */
  public Factor marginalize(Integer... varNumsToEliminate);

  /**
   * Returns a factor with the specified variables marginalized out by
   * summing/integrating. The returned {@code Factor} is defined over the the
   * difference between {@code this.getVars()} and {@code varNumsToEliminate}.
   * The probability that it returns for an assignment is the sum/integral over
   * all supersets of the assignment (that is, it sums/integrates over the
   * variables in {@code varNumsToEliminate}).
   */
  public Factor marginalize(Collection<Integer> varNumsToEliminate);

  /**
   * Same as {@link #maxMarginalize(Collection)}.
   * 
   * @param varNumsToEliminate
   * @return
   */
  public Factor maxMarginalize(Integer... varNumsToEliminate);

  /**
   * Returns a factor with the specified variables marginalized out by
   * maximizing. The returned {@code Factor} is defined over the the difference
   * between {@code this.getVars()} and {@code varNumsToEliminate}. The
   * probability that it returns for an assignment is the maximum over all
   * supersets of the assignment (that is, it maximizes over the variables in
   * {@code varNumsToEliminate}).
   */
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate);

  // TODO(jayant): Update the signature of maxMarginalize to this version in
  // the future. This
  // version essentially performs a beam search for the max marginal.
  // public Factor maxMarginalize(Collection<Integer> varNumsToEliminate,
  // int beamSize);

  /**
   * Adds {@code this} and {@code other} and returns the result. The density of
   * an assignment in the returned {@code Factor} is the sum of the densities
   * assigned by the added factors. {@code other} must contain the same
   * variables as this factor.
   * <p>
   * This operation is optional and extremely likely to be unsupported. Use
   * sparingly.
   */
  public Factor add(Factor other);

  /**
   * Adds {@code this} and all of the factors {@code others}, returning the
   * result. {@code other} must contain the same variables as this factor.
   * Equivalent to repeatedly invoking {@link #add(Factor)}, but may be faster.
   */
  public Factor add(List<Factor> others);

  /**
   * Returns the maximum between {@code this} and {@code other}. The density of
   * an assignment in the returned {@code Factor} is the maximum of the
   * densities assigned by {@code this} and {@code other}. {@code other} must
   * contain the same variables as this factor.
   * <p>
   * This operation is optional and extremely likely to be unsupported. Use
   * sparingly.
   */
  public Factor maximum(Factor other);

  /**
   * Returns the maximum between {@code this} and all of {@code factors},
   * returning the result. All of {@code factors} must contain the same
   * variables as this factor. Equivalent to repeatedly invoking
   * {@link #maximum(Factor)}, but may be faster.
   */
  public Factor maximum(List<Factor> factors);

  /**
   * Multiplies this factor by {@code other} and returns the result. {@code
   * other} must contain a subset of the variables in this factor. The
   * probability of an assignment in the returned factor is equal to the
   * products of the probabilities of the assignment in {@code this} and {@code
   * other}.
   * <p>
   * Not all subsets are necessarily supported.
   */
  public Factor product(Factor other);

  /**
   * Multiplies this factor by all of the passed-in factors. Equivalent to
   * repeatedly calling {@link #product(Factor)} with each factor in the list,
   * but may be faster.
   * 
   * @param others
   * @return
   */
  public Factor product(List<Factor> others);

  /**
   * Multiplies this factor by a constant weight.
   * 
   * @param constant
   * @return
   */
  public Factor product(double constant);

  /**
   * Samples a random assignment to the variables in this factor according to
   * this factor's probability distribution.
   */
  public Assignment sample();

  /**
   * Gets the {@code numAssignments} most probable assignments for this {@code
   * Factor}. Not all {@code Factor}s support retrieving more than one likely
   * {@code Assignment}, but all factors support retrieving one assignment. For
   * example, factors with continuous probability distributions only support
   * retrieving a single maximum probability assignment.
   * 
   * @param numAssignments
   * @return
   */
  public List<Assignment> getMostLikelyAssignments(int numAssignments);

  /**
   * Computes the expected value of a feature function (defined over the same
   * set of variables as this factor).
   */
  public double computeExpectation(FeatureFunction feature);

  // Coercion methods

  /**
   * Attempts to convert {@code this} into a {@link DiscreteFactor}.
   * 
   * @throws FactorCoercionError if {@code this} cannot be converted into a
   * {@link DiscreteFactor}.
   */
  public DiscreteFactor coerceToDiscrete();
}
