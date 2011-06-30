package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.factors.DiscreteFactor;
import com.jayantkrish.jklol.models.factors.TableFactor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An implementation of Gibbs sampling for computing approximate marginals.
 */
public class GibbsSampler implements InferenceEngine {

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

	public void setFactorGraph(FactorGraph f) {
		factorGraph = f;
	}

	private void doSamplingRound() {
		for (int i = 0; i < factorGraph.getVariables().size(); i++) {
			doSample(i);
		}
	}

	private void doSample(int varNum) {
		// Retain the assignments to all other variables.
		Assignment otherVarAssignment = curAssignment.removeAll(
				Collections.singletonList(varNum));

		// Multiply together all of the factors which define a probability distribution over 
		// variable varNum, conditioned on all other variables.
		Set<Integer> factorNums = factorGraph.getFactorsWithVariable(varNum);
		List<DiscreteFactor> factorsToCombine = new ArrayList<DiscreteFactor>();
		for (Integer factorNum : factorNums) {
			factorsToCombine.add((DiscreteFactor) factorGraph.getFactorFromIndex(factorNum).conditional(otherVarAssignment));
		}
		DiscreteFactor toSampleFrom = TableFactor.productFactor(factorsToCombine);

		// Draw the sample and update the sampler's current assignment. 
		Assignment subsetValues = toSampleFrom.sample();
		curAssignment = otherVarAssignment.jointAssignment(subsetValues);
	}

	public void computeMarginals() {
		computeMarginals(Assignment.EMPTY);
	}

	public void computeMarginals(Assignment assignment) {
		List<Variable> marginalVars = new ArrayList<Variable>();
		for (int varNum : assignment.getVarNumsSorted()) {
			marginalVars.add(factorGraph.getVariableFromIndex(varNum));
		}

		samples = new ArrayList<Assignment>();
		// Initialize sampler with an arbitrary assignment.
		List<Integer> varNums = new ArrayList<Integer>();
		List<Variable> vars = factorGraph.getVariables();
		List<Object> values = new ArrayList<Object>();
		for (int i = 0; i < vars.size(); i++) {
			varNums.add(i);
			values.add(vars.get(i).getArbitraryValue());
		}
		Assignment curAssignment = new Assignment(varNums, values);

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

	public DiscreteFactor getMarginal(List<Integer> varNumsToRetain) {
		assert samples != null;
		throw new UnsupportedOperationException("Not yet implemented");
	}

	/**
	 * GibbsSampler cannot compute max marginals. Throws a runtime exception if called.
	 */
	public void computeMaxMarginals() {
		computeMaxMarginals(null);
	}

	/**
	 * GibbsSampler cannot compute max marginals. Throws a runtime exception if called.
	 */
	public void computeMaxMarginals(Assignment assignment) {
		throw new UnsupportedOperationException("Max marginals are not supported by Gibbs sampling");
	}
}
