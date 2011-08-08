package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;

/**
 * A FactorGraphFactory is an interface which helps represent data-dependent
 * graphical models. These models dynamically construct a {@link FactorGraph}
 * based on the training example.
 * 
 * @param <T> type of {@code FactorGraph} constructed
 * @param <E> type of the training examples used to construct the {@code
 *            FactorGraph}.
 */
public interface FactorGraphFactory<T extends FactorGraph, E> {

	/**
	 * Construct the factor graph and an assignment to its variables which
	 * corresponds to the given training example.
	 */
	public Pair<T, Assignment> instantiateFactorGraph(E ex);
}