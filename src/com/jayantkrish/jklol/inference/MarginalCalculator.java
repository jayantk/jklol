package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.models.FactorGraph;

/**
 * An MarginalCalculator is an algorithm for computing marginal distributions
 * of a factor graph.
 */
public interface MarginalCalculator {

	/**
	 * Compute (unconditional) marginal distributions over the factors in the factor graph.
	 */
	public MarginalSet computeMarginals(FactorGraph factorGraph);

	/**
	 * Compute unconditional max marginals.
	 */
	public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph);
}
