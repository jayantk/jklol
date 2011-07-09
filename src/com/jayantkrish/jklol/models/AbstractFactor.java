package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.Assignment;



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
	
	/**
	 * Compute the unnormalized probability of an outcome.
	 */
	@Override
	public double getUnnormalizedProbability(List<? extends Object> outcome) {
		Preconditions.checkNotNull(outcome);
		
		Assignment a = getVars().outcomeToAssignment(outcome);
		return getUnnormalizedProbability(a);
	}

	
	@Override
	public Factor conditional(Assignment a) {
		VariableNumMap factorVars = vars.intersection(a.getVarNumsSorted());
		Assignment subAssignment = a.subAssignment(factorVars);
		TableFactor tableFactor = new TableFactor(factorVars);
		tableFactor.setWeight(subAssignment, 1.0);
		return conditional(tableFactor);
	}
	
	@Override
	public Factor conditional(Factor f) {
		return FactorMath.product(this, f);
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
