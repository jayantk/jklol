package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.inference.InferenceEngine;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.CptFactor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Train the weights of a factor graph using incremental EM. 
 * (Incremental EM is an online variant of EM.)
 */
public class IncrementalEMTrainer {

	private InferenceEngine inferenceEngine;
	private Factor[][] exampleCptMarginalMap;
	private int numIterations;
	private double smoothing;
	private LogFunction log;

	public IncrementalEMTrainer(int numIterations, double smoothing, InferenceEngine inferenceEngine) {
		this.exampleCptMarginalMap = null;
		this.smoothing = smoothing;
		this.numIterations = numIterations;
		this.inferenceEngine = inferenceEngine;
		this.log = null;
	}

	public IncrementalEMTrainer(int numIterations, double smoothing, 
			InferenceEngine inferenceEngine, LogFunction log) {
		this.exampleCptMarginalMap = null;
		this.smoothing = smoothing;
		this.numIterations = numIterations;
		this.inferenceEngine = inferenceEngine;
		this.log = log;
	}

	public void train(BayesNet bn, List<Assignment> trainingData) {

		initializeCpts(bn);
		inferenceEngine.setFactorGraph(bn);

		List<CptFactor> cptFactors = bn.getCptFactors();
		exampleCptMarginalMap = new Factor[trainingData.size()][cptFactors.size()];

		for (int i = 0; i < numIterations; i++) {
			if (log != null) {log.notifyIterationStart(i);}
			Collections.shuffle(trainingData);
			for (int j = 0; j < trainingData.size(); j++) {
				Assignment trainingExample = trainingData.get(j);
				if (log != null) {log.log(i, j, trainingExample, bn);}

				if (i > 0) {
					// Subtract out old statistics if they exist.
					for (int k = 0; k < cptFactors.size(); k++) {
						Factor oldMarginal = exampleCptMarginalMap[j][k];
						cptFactors.get(k).incrementOutcomeCount(oldMarginal, -1.0);
					}
				}
				// Update new sufficient statistics
				MarginalSet marginals = inferenceEngine.computeMarginals(trainingExample);
				for (int k = 0; k < cptFactors.size(); k++) {
					CptFactor cptFactor = cptFactors.get(k);
					Factor marginal = marginals.getMarginal(cptFactor.getVars().getVariableNums());
					exampleCptMarginalMap[j][k] = marginal;
					cptFactor.incrementOutcomeCount(marginal, 1.0);

					if (log != null) {log.log(i, j, cptFactor, marginal, bn);}
				}
			}
			if (log != null) {log.notifyIterationEnd(i);}
		}
	}

	private void initializeCpts(BayesNet bn) {
		// Set all CPT statistics to the smoothing value
		for (CptFactor cptFactor : bn.getCptFactors()) {
			cptFactor.clearCpt();
			cptFactor.addUniformSmoothing(smoothing);
		}
	}
}