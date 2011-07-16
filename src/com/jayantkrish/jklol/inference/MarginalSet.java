package com.jayantkrish.jklol.inference;

import java.util.List;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;

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
	public Factor getMarginal(List<Integer> varNums);
	
	/**
	 * Get the marginal distribution associated with the given variables as a {@link Factor} 
	 * @param vars
	 * @return
	 */
	public Factor getMarginal(VariableNumMap vars);
	
}
