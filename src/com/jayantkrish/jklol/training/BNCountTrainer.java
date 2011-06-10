package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.*;
import com.jayantkrish.jklol.util.Pair;

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
	train(new BayesNetWrapperFactory(bn), trainingData);
    }

    public <E> void train(BayesNetFactory<E> bnFactory, List<E> factoryExamples) {
	bnFactory.addUniformSmoothing(smoothingCounts);

	// For each training example, increment sufficient statistics appropriately.
	for (E trainingExample : factoryExamples) {
	    Pair<BayesNet, Assignment> p = bnFactory.instantiateFactorGraph(trainingExample);
	    BayesNet bn = p.getLeft();
	    Assignment assignment = p.getRight();
	    double probability = 1.0;
	    for (CptFactor cptFactor : bn.getCptFactors()) {
		cptFactor.incrementOutcomeCount(assignment, probability);
	    }
	}
    }
}