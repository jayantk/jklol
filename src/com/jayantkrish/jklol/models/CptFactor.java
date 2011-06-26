package com.jayantkrish.jklol.models;

import java.util.*;
import java.lang.StringBuilder;


/**
 * A CptFactor is a factor in a Bayes Net, parameterized by one or more
 * conditional probability tables.
 */
public abstract class CptFactor extends DiscreteFactor {

    public CptFactor(VariableNumMap vars) {
	super(vars);
    }

    /**
     * Clears all sufficient statistics accumulated in the CPTs.
     */
    public abstract void clearCpt();

    /**
     * Adds uniform smoothing to the CPTs
     */ 
    public abstract void addUniformSmoothing(double virtualCounts);

    /**
     * Update the probability of an assignment by adding count
     * to its sufficient statistics.
     *
     * This method is equivalent to calling the other incrementOutcomeCount
     * with a factor representing a point distribution on assignment
     */
    public abstract void incrementOutcomeCount(Assignment assignment, double count);

    /**
     * Update the probability of an assignment by adding count * marginal
     * to each assignment represented in marginal.
     */ 
    public abstract void incrementOutcomeCount(DiscreteFactor marginal, double count);

}

