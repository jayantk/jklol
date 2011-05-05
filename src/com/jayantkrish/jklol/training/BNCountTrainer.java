package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.*;

import java.util.*;

/**
 * Trains a Bayes Net using empirical CPT counts.
 */
public class BNCountTrainer {

    private int smoothingCounts;

    public BNCountTrainer(int smoothingCounts) {
	this.smoothingCounts = smoothingCounts;
    }

    public void train(BayesNet bn, List<Assignment> trainingData) {

	// Set all CPT statistics to the smoothing value
	for (CptFactor cptFactor : bn.getCptFactors()) {
	    cptFactor.clearCpt();
	    cptFactor.addUniformSmoothing(smoothingCounts);
	}

	// For each training example, increment sufficient statistics appropriately.
	for (Assignment trainingExample : trainingData) {
	    double probability = 1.0;
	    for (CptFactor cptFactor : bn.getCptFactors()) {
		cptFactor.incrementOutcomeCount(trainingExample, probability);
	    }
	}
    }
}