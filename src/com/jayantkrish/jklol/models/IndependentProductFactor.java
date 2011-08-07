package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * IndependentProductFactor represents a distribution which factorizes as 
 * the product of several distributions over disjoint sets of variables.
 * @author jayant
 *
 */
public class IndependentProductFactor extends AbstractFactor {

	private List<Factor> factors;
	
	/**
	 * Construct a factor whose probability distribution is the product of baseFactors.
	 * The factors in baseFactors cannot share any variable numbers (the intersection of their
	 * VariableNumMaps must be the empty set).  
	 * @param baseFactors
	 */
	public IndependentProductFactor(Collection<Factor> baseFactors) {
		super(getUnionVarNumMap(baseFactors));
		Preconditions.checkNotNull(baseFactors);
		// Check the independence condition while creating a mapping from variable numbers to
		// the factors they are present in.
		Map<Integer, Factor> variableFactors = Maps.newHashMap();
		for (Factor factor : baseFactors) {
			for (Integer varNum : factor.getVars().getVariableNums()) {
				Preconditions.checkArgument(!variableFactors.containsKey(varNum));
				variableFactors.put(varNum, factor);
			}
		}
		
		factors = Lists.newArrayList(baseFactors);
	}
	
	@Override
	public double computeExpectation(FeatureFunction feature) {
		throw new UnsupportedOperationException("Not yet implemented.");
	}

	@Override
	public Factor conditional(Assignment assignment) {
		List<Factor> conditionals = Lists.newArrayListWithCapacity(factors.size());
		for (Factor factor : factors) {
			conditionals.add(factor.conditional(assignment));
		}
		return new IndependentProductFactor(conditionals);
	}

	@Override
	public double getPartitionFunction() {
		double partitionFunction = 1.0;
		for (Factor factor : factors) {
			partitionFunction *= factor.getPartitionFunction();
		}
		return partitionFunction;
	}

	@Override
	public double getUnnormalizedProbability(Assignment assignment) {
		double probability = 1.0;
		for (Factor factor : factors) {
			probability *= factor.getUnnormalizedProbability(assignment);
		}
		return probability;
	}

	@Override
	public Factor marginalize(Collection<Integer> varNumsToEliminate) {
		List<Factor> marginals = Lists.newArrayListWithCapacity(factors.size());
		for (Factor factor : factors) {
			marginals.add(factor.marginalize(varNumsToEliminate));
		}
		return new IndependentProductFactor(marginals);
	}

	@Override
	public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
		List<Factor> maxMarginals = Lists.newArrayListWithCapacity(factors.size());
		for (Factor factor : factors) {
			maxMarginals.add(factor.maxMarginalize(varNumsToEliminate));
		}
		return new IndependentProductFactor(maxMarginals);
	}

	@Override
	public Assignment sample() {
		Assignment sample = Assignment.EMPTY;
		for (Factor factor : factors) {
			sample = sample.jointAssignment(factor.sample());
		}
		return sample;
	}

	/*
	 * Creates a VariableNumMap containing the union of the of the variable sets of 
	 * the passed-in factors.
	 */
	private static VariableNumMap getUnionVarNumMap(Collection<Factor> factors) {
		List<VariableNumMap> varNumMaps = Lists.newArrayList();
		for (Factor factor : factors) {
			varNumMaps.add(factor.getVars());
		}
		return VariableNumMap.unionAll(varNumMaps);
	}	
}
