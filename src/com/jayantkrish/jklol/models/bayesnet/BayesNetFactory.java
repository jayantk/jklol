package com.jayantkrish.jklol.models.bayesnet;

import com.jayantkrish.jklol.models.FactorGraphFactory;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;

/**
 * Dynamically constructs {@link BayesNet}s from training examples.
 */
public interface BayesNetFactory<E> extends FactorGraphFactory<BayesNet, E> {

	public Pair<BayesNet, Assignment> instantiateFactorGraph(E ex);

	public void addUniformSmoothing(double smoothingCounts);
}