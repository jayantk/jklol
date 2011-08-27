package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An implementation of Gibbs sampling for computing approximate marginals.
 * 
 * Does not work on FactorGraphs with 0 probability outcomes! 
 */
public class GibbsSampler extends AbstractMarginalCalculator {

	private int burnInSamples;
	private int numDrawsInMarginal;
	private int samplesBetweenDraws;

	public GibbsSampler(int burnInSamples, int numDrawsInMarginal, int samplesBetweenDraws) {
		this.burnInSamples = burnInSamples;
		this.numDrawsInMarginal = numDrawsInMarginal;
		this.samplesBetweenDraws = samplesBetweenDraws;
	}

	@Override
	public MarginalSet computeMarginals(FactorGraph factorGraph, Assignment assignment) {
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
			samples.add(curAssignment);
		}
		return new SampleMarginalSet(factorGraph.getVariableNumMap(), samples);
	}

	/**
	 * GibbsSampler cannot compute max marginals. Throws a runtime exception if called.
	 */
	@Override
	public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph, Assignment assignment) {
		throw new UnsupportedOperationException("Max marginals are not supported by Gibbs sampling");
	}

	/*
	 * Set the assignment variable to an arbitrary initial value.
	 */
	private Assignment initializeAssignment(FactorGraph factorGraph) {
		// Select the initial assignment.
		List<Variable> variables = factorGraph.getVariables();
		List<Integer> varNums = Lists.newArrayList();
		List<Object> values = Lists.newArrayList();
		for (int i = 0; i < variables.size(); i++) {
			varNums.add(i);
			values.add(variables.get(i).getArbitraryValue());
		}
		return new Assignment(varNums, values);
	}

	/*
	 * Sample each variable in the factor graph once.
	 */
	private Assignment doSamplingRound(FactorGraph factorGraph, Assignment curAssignment) {
	  Assignment assignment = curAssignment;
		for (int i = 0; i < factorGraph.getVariables().size(); i++) {
			assignment = doSample(factorGraph, assignment, i);
		}
		return assignment;
	}

	/*
	 * Resample the specified variable conditioned on all of the other variables.
	 */
	private Assignment doSample(FactorGraph factorGraph, Assignment curAssignment, int varNum) {
		// Retain the assignments to all other variables.
		Assignment otherVarAssignment = curAssignment.removeAll(
				Collections.singletonList(varNum));

		// Multiply together all of the factors which define a probability distribution over 
		// variable varNum, conditioned on all other variables.
		Set<Integer> factorNums = factorGraph.getFactorsWithVariable(varNum);
		List<Factor> factorsToCombine = new ArrayList<Factor>();
		for (Integer factorNum : factorNums) {
			Factor conditional = factorGraph.getFactor(factorNum)
				.conditional(otherVarAssignment);
			factorsToCombine.add(conditional.marginalize(otherVarAssignment.getVarNumsSorted()));
		}
		Factor toSampleFrom = factorsToCombine.get(0).product(factorsToCombine.subList(1, factorsToCombine.size()));
		// Draw the sample and update the sampler's current assignment. 
		Assignment subsetValues = toSampleFrom.sample();
		
		return otherVarAssignment.jointAssignment(subsetValues);
	}
}