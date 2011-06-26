package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;

/**
 * A Factor represents a probability distribution over a set of variables.
 *  
 * @author jayant
 *
 */
public interface Factor {
	
    public List<Integer> getVarNums();
    
    public List<Variable<?>> getVars();
	
	public double getUnnormalizedProbability(Assignment assignment);
	
	public double getPartitionFunction();
	    
    public DiscreteFactor conditional(Assignment a);

    public DiscreteFactor marginalize(Collection<Integer> varNumsToEliminate);
    
    public DiscreteFactor maxMarginalize(Collection<Integer> varNumsToEliminate);
    
    public DiscreteFactor maxMarginalize(Collection<Integer> varNumsToEliminate, int beamSize);
    
    public Assignment sample();
    
    public double computeExpectation(FeatureFunction feature);
}
