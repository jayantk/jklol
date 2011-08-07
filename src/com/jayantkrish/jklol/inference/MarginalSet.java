package com.jayantkrish.jklol.inference;

import java.util.Collection;

import com.jayantkrish.jklol.models.Factor;

/**
 * Represents a set of (possibly approximate) marginal distributions over a set of variables. 
 * @author jayant
 *
 */
public interface MarginalSet {

	/**
	 * Get the marginal distribution associated with the given variables as a {@link Factor} 
	 * @param varNums
	 * @return
	 */
	public Factor getMarginal(Collection<Integer> varNums);	
}
