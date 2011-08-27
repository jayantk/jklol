package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.models.loglinear.LogLinearModel;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Trains the weights of a factor graph using stochastic gradient descent.
 */
public class StochasticGradientTrainer {

	private MarginalCalculator inferenceEngine;
	private int numIterations;

	// Cache gradient map so we don't continuously reallocate memory for it.
	private Map<FeatureFunction, Double> gradient;

	public StochasticGradientTrainer(MarginalCalculator inferenceEngine, int numIterations) {
		this.inferenceEngine = inferenceEngine;
		this.numIterations = numIterations;
		gradient = new HashMap<FeatureFunction, Double>();
	}

	public void train(LogLinearModel factorGraph, List<Assignment> trainingData) {

		Collections.shuffle(trainingData);
		for (int i = 0; i < numIterations; i++) {
			for (Assignment trainingExample : trainingData) {
				gradient.clear();
				computeGradient(factorGraph, trainingExample);
				factorGraph.getFeatureSet().incrementFeatureWeights(gradient);
			}
		}
	}

	/*
	 * Computes the gradient and stores it in the gradient accumulator.
	 */
	private void computeGradient(LogLinearModel factorGraph, Assignment trainingExample) {
		// Compute the second term of the gradient, the unconditional expected feature counts
    MarginalSet expectedFeatureMarginals = inferenceEngine.computeMarginals(factorGraph);
		for (DiscreteLogLinearFactor factor : factorGraph.getLogLinearFactors()) {
			Factor marginal = expectedFeatureMarginals.getMarginal(factor.getVars().getVariableNums());
			for (FeatureFunction f : factor.getFeatures()) {
				if (!gradient.containsKey(f)) {
					gradient.put(f, 0.0);
				}
				gradient.put(f, 
						gradient.get(f) - marginal.computeExpectation(f));
			}
		}

	  // Compute the first term of the gradient, the model expectations conditioned on the training example.
    MarginalSet conditionalMarginals = inferenceEngine.computeMarginals(factorGraph, trainingExample);
		for (DiscreteLogLinearFactor factor : factorGraph.getLogLinearFactors()) {
			Factor marginal = conditionalMarginals.getMarginal(factor.getVars().getVariableNums());
			for (FeatureFunction f : factor.getFeatures()) {
				if (!gradient.containsKey(f)) {
					gradient.put(f, 0.0);
				}
				gradient.put(f, 
						gradient.get(f) + marginal.computeExpectation(f));
			}
		}
	}
}