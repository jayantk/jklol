package com.jayantkrish.jklol.inference;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;

/**
 * Stores a set of {@link Factor}s representing marginal distributions and uses them
 * to answer queries for marginals.
 * @author jayant
 *
 */
public class FactorMarginalSet implements MarginalSet {

	private List<Factor> factors;
	
	public FactorMarginalSet(List<Factor> factors) {
		this.factors = Lists.newArrayList(factors);
	}
	
	@Override
	public Factor getMarginal(List<Integer> varNums) {
		
	}

}
