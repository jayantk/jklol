package com.jayantkrish.jklol.models.bayesnet;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A CptFactor is a factor that's parameterized by a conditional probability table. 
 * This interface exports methods which enable the user to manipulate the CPT.  
 */
public interface CptFactor extends Factor {

	/**
	 * Clears all sufficient statistics accumulated in the CPTs.
	 */
	public abstract void clearCpt();

	/**
	 * Adds uniform smoothing to the CPTs
	 */ 
	public abstract void addUniformSmoothing(double virtualCounts);

	/**
	 * Update the probability of an assignment by adding count
	 * to its sufficient statistics.
	 *
	 * This method is equivalent to calling the other incrementOutcomeCount
	 * with a factor representing a point distribution on assignment
	 */
	public abstract void incrementOutcomeCount(Assignment assignment, double count);

	/**
	 * Update the probability of an assignment by adding count * marginal
	 * to each assignment represented in marginal.
	 */ 
	public abstract void incrementOutcomeCount(Factor marginal, double count);
}

