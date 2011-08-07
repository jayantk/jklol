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
		Preconditions.checkNotNull(vars);
		this.vars = vars;
	}

	@Override
	public VariableNumMap getVars() {
		return vars;
	}
	
	@Override
	public double getUnnormalizedProbability(List<? extends Object> outcome) {
		Preconditions.checkNotNull(outcome);
		Preconditions.checkArgument(outcome.size() == getVars().size());
		
		Assignment a = getVars().outcomeToAssignment(outcome);
		return getUnnormalizedProbability(a);
	}
	
	@Override
	public double getUnnormalizedProbability(Object ... outcome) {
		return getUnnormalizedProbability(Arrays.asList(outcome));
	}
	
	@Override
	public Factor marginalize(Integer ... varNums) {
		return marginalize(Arrays.asList(varNums));
	}

	@Override
	public Factor maxMarginalize(Integer ... varNums) {
		return maxMarginalize(Arrays.asList(varNums));
	}
	
	@Override
	public Factor add(List<Factor> others) {
		Factor current = this;
		for (Factor other : others) {
			current = current.add(other);
		}
		return current;
	}
	
	@Override
	public Factor product(List<Factor> others) {
		Factor current = this;
		for (Factor other : others) {
			current = current.product(other);
		}
		return current;
	}
}
