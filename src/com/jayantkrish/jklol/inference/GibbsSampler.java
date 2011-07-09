package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.FactorMath;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An implementation of Gibbs sampling for computing approximate marginals.
 */
public class GibbsSampler extends AbstractInferenceEngine {

	private FactorGraph factorGraph;

	private int burnInSamples;
	private int numDrawsInMarginal;
	private int samplesBetweenDraws;

	private Assignment curAssignment;
	private List<Assignment> samples;

	public GibbsSampler(int burnInSamples, int numDrawsInMarginal, int samplesBetweenDraws) {
		this.burnInSamples = burnInSamples;
		this.numDrawsInMarginal = numDrawsInMarginal;
		this.samplesBetweenDraws = samplesBetweenDraws;
		this.curAssignment = null;
		this.factorGraph = null;
		this.samples = null;
	}

	@Override
	public void setFactorGraph(FactorGraph f) {
		factorGraph = f;
	}

	@Override
	public void computeMarginals(Assignment assignment) {
		initializeAssignment();
		samples = new ArrayList<Assignment>();

		// Burn in the sampler
		for (int i = 0; i < burnInSamples; i++) {
			doSamplingRound();
		}

		// Draw the samples which will make up the approximate marginal.
		for (int numDraws = 0; numDraws < numDrawsInMarginal; numDraws++) {
			for (int i = 0; i < samplesBetweenDraws; i++) {
				doSamplingRound();
			}
			samples.add(curAssignment);
		}
	}

	public Factor getMarginal(List<Integer> varNumsToRetain) {
		Preconditions.checkNotNull(varNumsToRetain);
		Preconditions.checkNotNull(samples);
		return samplesToFactor(samples, varNumsToRetain);
	}

	/**
	 * GibbsSampler cannot compute max marginals. Throws a runtime exception if called.
	 */
	public void computeMaxMarginals(Assignment assignment) {
		throw new UnsupportedOperationException("Max marginals are not supported by Gibbs sampling");
	}

	/*
	 * Set the assignment variable to an arbitrary initial value.
	 */
	private void initializeAssignment() {
		// Select the initial assignment.
		List<Variable> variables = factorGraph.getVariables();
		List<Integer> varNums = Lists.newArrayList();
		List<Object> values = Lists.newArrayList();
		for (int i = 0; i < variables.size(); i++) {
			varNums.add(i);
			values.add(variables.get(i).getArbitraryValue());
		}
		curAssignment = new Assignment(varNums, values);
	}

	/*
	 * Sample each variable in the factor graph once.
	 */
	private void doSamplingRound() {
		for (int i = 0; i < factorGraph.getVariables().size(); i++) {
			doSample(i);
		}
	}

	/*
	 * Resample the specified variable conditioned on all of the other variables.
	 */
	private void doSample(int varNum) {
		// Retain the assignments to all other variables.
		Assignment otherVarAssignment = curAssignment.removeAll(
				Collections.singletonList(varNum));

		// Multiply together all of the factors which define a probability distribution over 
		// variable varNum, conditioned on all other variables.
		Set<Integer> factorNums = factorGraph.getFactorsWithVariable(varNum);
		List<Factor> factorsToCombine = new ArrayList<Factor>();
		for (Integer factorNum : factorNums) {
			Factor conditional = factorGraph.getFactorFromIndex(factorNum)
				.conditional(otherVarAssignment);
			factorsToCombine.add(conditional.marginalize(otherVarAssignment.getVarNumsSorted()));
		}
		Factor toSampleFrom = FactorMath.product(factorsToCombine);
		// Draw the sample and update the sampler's current assignment. 
		Assignment subsetValues = toSampleFrom.sample();
		curAssignment = otherVarAssignment.jointAssignment(subsetValues);
	}
	
	private Factor samplesToFactor(List<Assignment> sampledAssignments, List<Integer> varNumsToRetain) {
		Preconditions.checkNotNull(sampledAssignments);
		Preconditions.checkArgument(sampledAssignments.size() > 0);
		VariableNumMap varsToRetain = factorGraph.getVariableNumMap().intersection(varNumsToRetain);
		TableFactor factor = new TableFactor(varsToRetain);
		for (Assignment sample : sampledAssignments) {
			Assignment factorSample = sample.subAssignment(varNumsToRetain);
			factor.setWeight(factorSample, 
					factor.getUnnormalizedProbability(factorSample) + 1.0);
		}
		return factor;
	}
}