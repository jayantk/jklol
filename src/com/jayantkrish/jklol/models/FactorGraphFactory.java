package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;

/**
 * A FactorGraphFactory is an interface which helps represent
 * data-dependent graphical models. In these models, the FactorGraph
 * is dynamically constructed based on the training example, which can
 * have an arbitrary type.
 */
public interface FactorGraphFactory<T extends FactorGraph, E> {

	/**
	 * Construct the factor graph and an assignment to its variables
	 * which corresponds to the given training example.
	 */
	public Pair<T, Assignment> instantiateFactorGraph(E ex);
}