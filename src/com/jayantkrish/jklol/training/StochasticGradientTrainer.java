package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayantkrish.jklol.inference.InferenceEngine;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.models.loglinear.LogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.LogLinearModel;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Trains the weights of a factor graph using stochastic gradient descent.
 */
public class StochasticGradientTrainer {

	private InferenceEngine inferenceEngine;
	private int numIterations;

	// Cache gradient map so we don't continuously reallocate memory for it.
	private Map<FeatureFunction, Double> gradient;

	public StochasticGradientTrainer(InferenceEngine inferenceEngine, int numIterations) {
		this.inferenceEngine = inferenceEngine;
		this.numIterations = numIterations;
		gradient = new HashMap<FeatureFunction, Double>();
	}

	public void train(LogLinearModel factorGraph, List<Assignment> trainingData) {
		inferenceEngine.setFactorGraph(factorGraph);

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
		// Compute the second term of the gradient, the expected feature counts
		inferenceEngine.computeMarginals();
		for (LogLinearFactor factor : factorGraph.getLogLinearFactors()) {
			Factor<?> marginal = inferenceEngine.getMarginal(factor.getVars().getVariableNums());
			for (FeatureFunction f : factor.getFeatures()) {
				if (!gradient.containsKey(f)) {
					gradient.put(f, 0.0);
				}
				gradient.put(f, 
						gradient.get(f) - marginal.computeExpectation(f));
			}
		}

		// Compute the first term of the gradient, the model expectations conditioned on the training example.
		inferenceEngine.computeMarginals(trainingExample);
		for (LogLinearFactor factor : factorGraph.getLogLinearFactors()) {
			Factor<?> marginal = inferenceEngine.getMarginal(factor.getVars().getVariableNums());
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