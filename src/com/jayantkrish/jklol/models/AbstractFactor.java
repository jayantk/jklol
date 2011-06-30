package com.jayantkrish.jklol.models;

import java.util.Arrays;



/**
 * AbstractFactor provides a partial implementation of Factor.
 * @author jayant
 *
 */
public abstract class AbstractFactor<T extends Variable> implements Factor<T> {

	private VariableNumMap<T> vars;

	public AbstractFactor(VariableNumMap<T> vars) {
		assert vars != null;
		this.vars = vars;
	}

	@Override
	public VariableNumMap<T> getVars() {
		return vars;
	}

	@Override
	public Factor<T> marginalize(Integer ... varNums) {
		return marginalize(Arrays.asList(varNums));
	}

	@Override
	public Factor<T> maxMarginalize(Integer ... varNums) {
		return maxMarginalize(Arrays.asList(varNums));
	}

}
