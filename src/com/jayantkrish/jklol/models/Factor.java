package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;

import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A Factor represents a probability distribution over a set of variables.
 *  
 * @author jayant
 *
 */
public interface Factor {

	/**
	 * Get the set of variables which this factor is defined over.
	 * @return
	 */
	public VariableNumMap getVars();

	/**
	 * Get the unnormalized probability of a particular assignment to the variables in this factor. 
	 * This method requires assignment to contain a superset of the variables in the factor. 
	 * 
	 * @param assignment
	 * @return
	 */
	public double getUnnormalizedProbability(Assignment assignment);
	
	/**
	 * Convenience method for getting the probability of an assignment. outcome contains the assignment
	 * to the variables in this factor, sorted in numerical order by their variable number.
	 */
	public double getUnnormalizedProbability(List<? extends Object> outcome);

	/**
	 * Returns the normalizing factor for the unnormalized probabilities returned by this factor. 
	 * @return
	 */
	public double getPartitionFunction();

	/**
	 * Get a new factor which conditions on the observed variables in the
	 * assignment. The returned factor contains the same variables as the original, but
	 * with the appropriate sections zeroed out.
	 */
	public Factor conditional(Assignment a);
	
	/**
	 * Get a new factor with a fixed probability distribution over the variables in f.
	 * This is a generalization of conditioning on a particular assignment (which is accomplished
	 * by setting f to a point distribution at the assignment.)
	 * @param f must contain a subset of the variables in {@code this}.
	 */
	public Factor conditional(Factor f);
	
	/**
	 * Return a factor with the specified variables marginalized out by summing.
	 */
	public Factor marginalize(Integer ... varNumsToEliminate);

	/**
	 * Return a factor with the specified variables marginalized out by summing.
	 */
	public Factor marginalize(Collection<Integer> varNumsToEliminate);

	/**
	 * Return a factor with the specified variables marginalized out by maximizing.
	 */
	public Factor maxMarginalize(Integer ... varNumsToEliminate);

	/**
	 * Return a factor with the specified variables marginalized out by maximizing.
	 */
	public Factor maxMarginalize(Collection<Integer> varNumsToEliminate);
	// TODO(jayant): Update the signature of maxMarginalize to this version in the future. This	
	// version essentially performs a beam search for the max marginal. 
	// public Factor<T> maxMarginalize(Collection<Integer> varNumsToEliminate, int beamSize);

	/**
	 * Sample a random assignment to the variables in this factor according to this factor's
	 * probability distribution.
	 */
	public Assignment sample();

	/**
	 * Compute the expected value of a feature function (over the same set of variables as this factor) 
	 */
	public double computeExpectation(FeatureFunction feature);
}
