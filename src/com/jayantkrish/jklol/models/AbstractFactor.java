package com.jayantkrish.jklol.models;

import java.util.Arrays;



/**
 * AbstractFactor provides a partial implementation of Factor.
 * @author jayant
 *
 */
public abstract class AbstractFactor implements Factor {

	private VariableNumMap vars;

	public AbstractFactor(VariableNumMap vars) {
		assert vars != null;
		this.vars = vars;
	}

	@Override
	public VariableNumMap getVars() {
		return vars;
	}

	@Override
	public Factor marginalize(Integer ... varNums) {
		return marginalize(Arrays.asList(varNums));
	}

	@Override
	public Factor maxMarginalize(Integer ... varNums) {
		return maxMarginalize(Arrays.asList(varNums));
	}

}
