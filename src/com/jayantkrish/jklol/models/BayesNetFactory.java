package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.*;
import java.util.*;

/**
 * A BayesNetFactory dynamically constructs Bayes Nets
 */
public interface BayesNetFactory<E> extends FactorGraphFactory<E> {

    public Pair<BayesNet, Assignment> instantiateFactorGraph(E ex);

    public void addUniformSmoothing(double smoothingCounts);
}