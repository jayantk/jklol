package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An MarginalCalculator is an algorithm for computing marginal distributions
 * of a factor graph.
 */
public interface MarginalCalculator {

	/**
	 * Give the inference engine a factor graph to perform inference on.
	 */
	public void setFactorGraph(FactorGraph f);

	/**
	 * Compute (unconditional) marginal distributions over the factors in the factor graph.
	 */
	public MarginalSet computeMarginals();

	/**
	 * Compute marginals conditioned on the provided variable assignments. Passing an empty
	 * assignment results in unconditional marginals.
	 */
	public MarginalSet computeMarginals(Assignment assignment);

	/**
	 * Compute unconditional max marginals.
	 */
	public MaxMarginalSet computeMaxMarginals();

	/**
	 * Compute max marginals conditioned on the provided assignment.
	 */
	public MaxMarginalSet computeMaxMarginals(Assignment assignment);

}
