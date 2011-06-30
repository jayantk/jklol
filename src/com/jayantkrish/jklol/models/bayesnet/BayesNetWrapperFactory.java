package com.jayantkrish.jklol.models.bayesnet;

import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;

/**
 * A BayesNetWrapperFactory wraps a BayesNet and returns it each time 
 * a graph is requested.
 */
public class BayesNetWrapperFactory implements BayesNetFactory<Assignment> {
	private BayesNet bn;

	public BayesNetWrapperFactory(BayesNet bn) {
		this.bn = bn;
	}

	public Pair<BayesNet, Assignment> instantiateFactorGraph(Assignment ex) {
		return new Pair<BayesNet, Assignment>(bn, ex);
	}

	public void addUniformSmoothing(double smoothingCounts) {
		// Set all CPT statistics to the smoothing value
		for (CptFactor<?> cptFactor : bn.getCptFactors()) {
			cptFactor.clearCpt();
			cptFactor.addUniformSmoothing(smoothingCounts);
		}
	}
}