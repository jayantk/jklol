package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.jayantkrish.jklol.models.Assignment;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;

/**
 * An implementation of Gibbs sampling for computing approximate marginals.
 */
public class GibbsSampler implements InferenceEngine {

	private FactorGraph factorGraph;

	private int burnInSamples;
	private int numDrawsInMarginal;
	private int samplesBetweenDraws;

	private Assignment curAssignment;
	private TableFactor marginal;

	public GibbsSampler(int burnInSamples, int numDrawsInMarginal, int samplesBetweenDraws) {
		this.burnInSamples = burnInSamples;
		this.numDrawsInMarginal = numDrawsInMarginal;
		this.samplesBetweenDraws = samplesBetweenDraws;
		this.curAssignment = null;
		this.factorGraph = null;
		this.marginal = null;
	}

	public void setFactorGraph(FactorGraph f) {
		factorGraph = f;
	}

	private void doSamplingRound() {
		for (Integer varNum : factorGraph.getVarNums()) {
			doSample(varNum);
		}
	}

	private void doSample(int varNum) {
		// TODO: subAssignment is like exactly wrong.
		Assignment a = curAssignment.subAssignment(
				Collections.singletonList(varNum));
		Set<Integer> factorNums = factorGraph.getFactorsWithVariable(varNum);
		DiscreteFactor toSampleFrom = null;
		for (Integer factorNum : factorNums) {
			DiscreteFactor next = factorGraph.getFactorFromIndex(factorNum).conditional(a);
			if (toSampleFrom == null) {
				toSampleFrom = next;
			} else {
				toSampleFrom = toSampleFrom.product(next);
			}
		}
		Assignment subsetValues = toSampleFrom.sample();
		a = a.jointAssignment(subsetValues);
	}
	
	public void computeMarginals() {
		computeMarginals(Assignment.EMPTY);
	}

	public void computeMarginals(Assignment assignment) {
		List<Variable> marginalVars = new ArrayList<Variable>();
		for (int varNum : assignment.getVarNumsSorted()) {
			marginalVars.add(factorGraph.getVariableFromIndex(varNum));
		}
		
		marginal = new TableFactor(assignment.getVarNumsSorted(), marginalVars);
		// Initialize sampler with an arbitrary assignment.
		List<Variable> vars = factorGraph.getVariables();
		List<Integer> valueNums = new ArrayList<Integer>();
		for (int i = 0; i < vars.size(); i++) {
			valueNums.add(vars.get(i).getArbitraryValueIndex());
		}
		Assignment curAssignment = new Assignment(factorGraph.getVarNums(), valueNums);

		// Burn in the sampler
		for (int i = 0; i < burnInSamples; i++) {
			doSamplingRound();
		}

		// Draw the samples which will make up the approximate marginal.
		for (int numDraws = 0; numDraws < numDrawsInMarginal; numDraws++) {
			for (int i = 0; i < samplesBetweenDraws; i++) {
				doSamplingRound();
			}
			double curWeight = marginal.getUnnormalizedProbability(curAssignment);
			marginal.setWeight(curAssignment, curWeight + 1.0);
		}
	}

	public DiscreteFactor getMarginal(List<Integer> varNumsToRetain) {
		assert marginal != null;
		Set<Integer> varNumsToEliminate = new HashSet<Integer>(marginal.getVarNums());
		varNumsToEliminate.removeAll(varNumsToRetain);
		return marginal.marginalize(varNumsToEliminate);
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
