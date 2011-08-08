package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;

import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A probability distribution over a set of variables. Factors are used in
 * {@link FactorGraph}s to represent graphical models.
 * 
 * @author jayant
 * 
 */
public interface Factor {

	/**
	 * Get the set of variables which this factor is defined over.
	 * 
	 * @return
	 */
	public VariableNumMap getVars();

	/**
	 * Get the unnormalized probability of a particular assignment to the
	 * variables in this factor. This method requires assignment to contain a
	 * superset of the variables in the factor.
	 * 
	 * @param assignment
	 * @return
	 */
	public double getUnnormalizedProbability(Assignment assignment);

	/**
	 * Convenience method for getting the probability of an assignment. {@code
	 * outcome} contains the assignment to the variables in this factor, sorted
	 * in numerical order by their variable number. See
	 * {@link #getUnnormalizedProbability(Assignment)}
	 */
	public double getUnnormalizedProbability(List<? extends Object> outcome);

	/**
	 * Convenience method for getting the probability of an assignment. {@code
	 * outcome} contains the assignment to the variables in this factor, sorted
	 * in numerical order by their variable number. See
	 * {@link #getUnnormalizedProbability(Assignment)}.
	 */
	public double getUnnormalizedProbability(Object... outcome);

	/**
	 * Get a new factor which conditions on the observed variables in the
	 * assignment. The returned factor contains the same variables as the
	 * original, but with the appropriate sections zeroed out.
	 */
	public Factor conditional(Assignment a);

	/**
	 * Return a factor with the specified variables marginalized out by summing.
	 */
	public Factor marginalize(Integer... varNumsToEliminate);

	/**
	 * Return a factor with the specified variables marginalized out by summing.
	 */
	public Factor marginalize(Collection<Integer> varNumsToEliminate);

	/**
	 * Return a factor with the specified variables marginalized out by
	 * maximizing.
	 */
	public Factor maxMarginalize(Integer... varNumsToEliminate);

	/**
	 * Return a factor with the specified variables marginalized out by
	 * maximizing.
	 */
	public Factor maxMarginalize(Collection<Integer> varNumsToEliminate);

	// TODO(jayant): Update the signature of maxMarginalize to this version in
	// the future. This
	// version essentially performs a beam search for the max marginal.
	// public Factor<T> maxMarginalize(Collection<Integer> varNumsToEliminate,
	// int beamSize);

	/**
	 * Adds {@code this} and {@code other} and returns the result. {@code other}
	 * must contain a subset of the variables in this factor. Not all factors
	 * support this operation.
	 */
	public Factor add(Factor other);

	/**
	 * Adds {@code this} and all of the factors {@code others}, returning the
	 * result. {@code other} must contain a subset of the variables in this
	 * factor. Equivalent to repeatedly invoking {@link #add(Factor)}, but may
	 * be faster.
	 */
	public Factor add(List<Factor> others);

	/**
	 * Multiplies this factor by the passed-in factor. {@code other} must
	 * contain a subset of the variables in this factor. Additionally, not all
	 * subsets are necessarily supported.
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
	 * @param constant
	 * @return
	 */
	public Factor product(double constant);
	
	/**
	 * Sample a random assignment to the variables in this factor according to
	 * this factor's probability distribution.
	 */
	public Assignment sample();

	/**
	 * Compute the expected value of a feature function (over the same set of
	 * variables as this factor)
	 */
	public double computeExpectation(FeatureFunction feature);

	// Coercion methods

	/**
	 * Attempts to convert {@code this} into a {@link DiscreteFactor}.
	 * 
	 * @throws FactorCoercionError
	 *             if {@code this} cannot be converted into a
	 *             {@link DiscreteFactor}.
	 */
	public DiscreteFactor coerceToDiscrete();
}
