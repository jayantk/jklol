package com.jayantkrish.jklol.models;

import java.util.*;

/**
 * A Markov Network with log-linear parameter weights, parameterized by an arbitrary set of
 * (clique-factored) feature functions.
 */ 
public class LogLinearModel extends FactorGraph {

    private List<LogLinearFactor> logLinearFactors;
    private FeatureSet features;

    /**
     * Create an empty log-linear model
     */
    public LogLinearModel () {
	super();
	// Track model features / which factors can be trained.
	features = new FeatureSet();
	logLinearFactors = new ArrayList<LogLinearFactor>();
    }

    /**
     * Adds a new Factor with log-linear weights connecting the specified variables.
     */
    public LogLinearFactor addLogLinearFactor(List<String> factorVariables) {
	List<Integer> varNums = new ArrayList<Integer>();
	List<Variable> vars = new ArrayList<Variable>();
	lookupVarStrings(factorVariables, varNums, vars);

	LogLinearFactor factor = new LogLinearFactor(varNums, vars, features);
	logLinearFactors.add(factor);
	addFactor(factor);
	return factor;
    }

    /**
     * Get the log-linear factors in the MN.
     */
    public List<LogLinearFactor> getLogLinearFactors() {
	return Collections.unmodifiableList(logLinearFactors);
    }

    /**
     * Get the features of this graph and their weights.
     */ 
    public FeatureSet getFeatureSet() {
	return features;
    }
}