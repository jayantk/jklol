package com.jayantkrish.jklol.models.factors;

import java.util.Collection;

import com.jayantkrish.jklol.models.FeatureFunction;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A Factor represents a probability distribution over a set of variables.
 *  
 * @author jayant
 *
 */
public interface Factor<T extends Variable> {
	
	/**
	 * Get the set of variables which this factor is defined over.
	 * @return
	 */
    public VariableNumMap<T> getVars();
	
    /**
     * Get the unnormalized probability of a particular assignment to the variables in this factor. 
     * This method requires assignment to contain a superset of the variables in the factor. 
     * 
     * @param assignment
     * @return
     */
	public double getUnnormalizedProbability(Assignment assignment);
	
	/**
	 * Returns the normalizing factor for the unnormalized probabilities returned by this factor. 
	 * @return
	 */
	public double getPartitionFunction();
	   
	/**
	 * Get a new factor which conditions on the observed variables in the
	 * assignment.
	 *
	 * The returned factor still contains the same variables as the original, but has appropriate
	 * portions of the factor distribution zeroed out.
	 */
    public Factor<T> conditional(Assignment a);

	/**
	 * Return a factor with the specified variables marginalized out by summing.
	 */
    public Factor<T> marginalize(Integer ... varNumsToEliminate);
    
	/**
	 * Return a factor with the specified variables marginalized out by summing.
	 */
    public Factor<T> marginalize(Collection<Integer> varNumsToEliminate);
    
	/**
	 * Return a factor with the specified variables marginalized out by maximizing.
	 */
    public Factor<T> maxMarginalize(Integer ... varNumsToEliminate);
    
    /**
	 * Return a factor with the specified variables marginalized out by maximizing.
	 */
    public Factor<T> maxMarginalize(Collection<Integer> varNumsToEliminate);
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
