package com.jayantkrish.jklol.cfg;

/**
 * A ProductionDistribution defines the probabilistic component of a pCFG.  It assigns
 * (unnormalized) probabilities to binary / terminal production rules.
 */
public interface ProductionDistribution {

    /**
     * Get the probability of a binary production rule
     */ 
    public double getRuleProbability(BinaryProduction rule);

    /**
     * Get the probability of a terminal production.
     */
    public double getTerminalProbability(TerminalProduction rule);

}