package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;

/**
 * A BayesNetFactory dynamically constructs Bayes Nets
 */
public interface BayesNetFactory<E> extends FactorGraphFactory<BayesNet, E> {

    public Pair<BayesNet, Assignment> instantiateFactorGraph(E ex);

    public void addUniformSmoothing(double smoothingCounts);
}