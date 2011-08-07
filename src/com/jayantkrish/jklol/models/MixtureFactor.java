package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;

public class MixtureFactor extends AbstractFactor {

	/** The set of variables from which drawnVars is chosen from. **/
	private final ImmutableList<VariableNumMap> drawingVars;
	private final ImmutableList<Factor> drawingFactors;
	private final ImmutableList<Double> drawingWeights;

	private final VariableNumMap drawnVars;
	private final Factor drawnFactor;
	
	/** The value of the partition function if all of drawingFactors were independent. **/  
	private final double independentPartitionFunction;

	/**
	 * In most cases, {@link #createMixtureFactor} is more convenient.
	 *  
	 * @param vars
	 * @param factors
	 * @param weights
	 * @param useSum
	 */
	public MixtureFactor(VariableNumMap vars, List<Factor> factors, 
			List<Double> weights, boolean useSum) {
		super(vars);
		this.factors = ImmutableList.copyOf(Preconditions.checkNotNull(factors));
		this.weights = ImmutableList.copyOf(Preconditions.checkNotNull(weights));
		this.useSum = useSum;
		Preconditions.checkArgument(factors.size() == weights.size());
		
		// For each assignment of the selector variable, all of the other drawingFactors are independent. 
		independentPartitionFunction = 1.0;
		for (int i = 0; i < drawingFactors.size(); i++) {
			independentPartitionFunction *= drawingFactors.get(i).getPartitionFunction();
		}

	}
	
	public static MixtureFactor createMixtureFactor(List<Factor> factors, 
			List<Double> weights, boolean useSum) {
		VariableNumMap allVars = VariableNumMap.emptyMap();
		for (Factor factor : factors) {
			allVars = allVars.union(factor.getVars());
		}
		return new MixtureFactor(allVars, factors, weights, useSum);
	}

	@Override
	public double computeExpectation(FeatureFunction feature) {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getPartitionFunction() {
		double partitionFunction = 0.0;
		for (int i = 0; i < drawingFactors.size(); i++) {
			partitionFunction += drawingWeights.get(i) 
			* drawnFactor.product(drawingFactors.get(i).mapToVariables(drawnVars)).getPartitionFunction() 
			* (independentPartitionFunction / drawingFactors.get(i).getPartitionFunction());
		}
		return partitionFunction;
	}

	@Override
	public double getUnnormalizedProbability(Assignment assignment) {
		Preconditions.checkNotNull(assignment);
		Preconditions.checkArgument(assignment.containsVars(getVars().getVariableNums()));
		List<Object> drawnValues = assignment.subAssignment(drawnVars).getVarValuesInKeyOrder();
		
		double probability = 0.0;
		for (int i = 0; i < drawingVars.size(); i++) {
			List<Object> drawingValues = assignment.subAssignment(drawingVars.get(i)).getVarValuesInKeyOrder();
			if (drawnValues.equals(drawingValues)) {
				probability += drawingWeights.get(i) 
				* drawnFactor.product(drawingFactors.get(i).mapToVariables(drawnVars)).getUnnormalizedProbability(assignment)
				* (independentPartitionFunction / 
			}
		}

	}

	@Override
	public Factor marginalize(Collection<Integer> varNumsToEliminate) {
		List<Factor> marginalizedFactors = Lists.newArrayList();
		for (int i = 0; i < factors.size(); i++) {
			marginalizedFactors.add(factors.get(i).marginalize(varNumsToEliminate));
		}
		return MixtureFactor.createMixtureFactor(marginalizedFactors, weights, true);
	}

	@Override
	public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
		List<Factor> maxMarginalizedFactors = Lists.newArrayList();
		for (int i = 0; i < factors.size(); i++) {
			maxMarginalizedFactors.add(factors.get(i).maxMarginalize(varNumsToEliminate));
		}
		return MixtureFactor.createMixtureFactor(maxMarginalizedFactors, weights, false);
	}
	
	@Override
	public Factor product(Factor other) {
		List<Factor> multipliedFactors = Lists.newArrayList();
		for (int i = 0; i < factors.size(); i++) {
			multipliedFactors.add(factors.get(i).product(other));
		}
		return new MixtureFactor(getVars(), multipliedFactors, weights, this.useSum);
	}

	@Override
	public Assignment sample() {
		List<Double> factorSampleWeights = Lists.newArrayList();
		double partitionFunction = 0.0;
		for (int i = 0; i < factors.size(); i++) {
			double factorWeight = weights.get(i) * factors.get(i).getPartitionFunction();
			factorSampleWeights.add(factorWeight);
			partitionFunction += factorWeight;
		}
		
		double draw = Math.random();
		double sumProb = 0.0;
		int i = 0;
		while (sumProb <= draw) {
			sumProb += weights.get(i) * factors.get(i).getPartitionFunction() / partitionFunction;
			i++;
		}
		i--;
		
		return factors.get(i).sample();
	}

	@Override
	public Factor conditional(Assignment a) {
		List<Factor> conditionedFactors = Lists.newArrayList();
		for (int i = 0; i < factors.size(); i++) {
			conditionedFactors.add(factors.get(i).conditional(a));
		}
		return new MixtureFactor(getVars(), conditionedFactors, weights, this.useSum);
	}
	
	@Override
	public DiscreteFactor coerceToDiscrete() {
		
	}
}
