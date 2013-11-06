package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An implementation of Gibbs sampling for computing approximate marginals.
 * <p>
 * Does not work on FactorGraphs with 0 probability outcomes! 
 */
public class GibbsSampler implements MarginalCalculator {
  private static final long serialVersionUID = 1L;

	private int burnInSamples;
	private int numDrawsInMarginal;
	private int samplesBetweenDraws;

	public GibbsSampler(int burnInSamples, int numDrawsInMarginal, int samplesBetweenDraws) {
		this.burnInSamples = burnInSamples;
		this.numDrawsInMarginal = numDrawsInMarginal;
		this.samplesBetweenDraws = samplesBetweenDraws;
	}

	@Override
	public MarginalSet computeMarginals(FactorGraph factorGraph) {
	  Assignment curAssignment = initializeAssignment(factorGraph);

		// Burn in the sampler
		for (int i = 0; i < burnInSamples; i++) {
			curAssignment = doSamplingRound(factorGraph, curAssignment);
		}

		// Draw the samples which will make up the approximate marginal.
    List<Assignment> samples = new ArrayList<Assignment>();
		for (int numDraws = 0; numDraws < numDrawsInMarginal; numDraws++) {
			for (int i = 0; i < samplesBetweenDraws; i++) {
				curAssignment = doSamplingRound(factorGraph, curAssignment);
			}
			curAssignment = doSamplingRound(factorGraph, curAssignment);
			samples.add(curAssignment);
		}
		return new SampleMarginalSet(factorGraph.getVariables(), samples, 
		    factorGraph.getConditionedVariables(), factorGraph.getConditionedValues());
	}

	/**
	 * GibbsSampler cannot compute max marginals. Throws a runtime exception if called.
	 */
	@Override
	public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph) {
		throw new UnsupportedOperationException("Max marginals are not supported by Gibbs sampling");
	}

	/*
	 * Set the assignment variable to an arbitrary initial value.
	 */
	private Assignment initializeAssignment(FactorGraph factorGraph) {
		// Select the initial assignment. 
	  // TODO: Perform a search to find an outcome with nonzero probability.
	  
		Variable[] variables = factorGraph.getVariables().getVariablesArray();
		int[] varNums = factorGraph.getVariables().getVariableNumsArray();
		Object[] values = new Object[variables.length];
		for (int i = 0; i < variables.length; i++) {
			values[i] = variables[i].getArbitraryValue();
		}
		return Assignment.fromSortedArrays(varNums, values);
	}

	/*
	 * Sample each variable in the factor graph once.
	 */
	private Assignment doSamplingRound(FactorGraph factorGraph, Assignment curAssignment) {
	  Assignment assignment = curAssignment;
	  int[] variableNums = factorGraph.getVariables().getVariableNumsArray();
		for (int i = 0; i < variableNums.length; i++) {
			assignment = doSample(factorGraph, assignment, variableNums[i]);
		}
		return assignment;
	}

	/*
	 * Resample the specified variable conditioned on all of the other variables.
	 */
	private Assignment doSample(FactorGraph factorGraph, Assignment curAssignment, int varNum) {
		// Retain the assignments to all other variables.
		Assignment otherVarAssignment = curAssignment.removeAll(varNum);

		// Multiply together all of the factors which define a probability distribution over 
		// variable varNum, conditioned on all other variables.
		Set<Integer> factorNums = factorGraph.getFactorsWithVariable(varNum);

		Preconditions.checkState(factorNums.size() > 0, "Variable not in factor: " + varNum + " " + factorNums);
		List<Factor> factorsToCombine = new ArrayList<Factor>();
		for (Integer factorNum : factorNums) {
			Factor conditional = factorGraph.getFactor(factorNum)
				.conditional(otherVarAssignment);
			factorsToCombine.add(conditional.marginalize(otherVarAssignment.getVariableNums()));
		}
		Factor toSampleFrom = factorsToCombine.get(0).product(factorsToCombine.subList(1, factorsToCombine.size()));
		// Draw the sample and update the sampler's current assignment. 
		Assignment subsetValues = toSampleFrom.sample();
		
		return otherVarAssignment.union(subsetValues);
	}
}