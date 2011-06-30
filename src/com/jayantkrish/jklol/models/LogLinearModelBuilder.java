package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.List;

import com.jayantkrish.jklol.models.factors.LogLinearFactor;

/**
 * A Markov Network with log-linear parameter weights, parameterized by an arbitrary set of
 * (clique-factored) feature functions.
 */ 
public class LogLinearModelBuilder {

	private FactorGraph factorGraph;	
	private List<LogLinearFactor> logLinearFactors;
	private FeatureSet features;

	private VariableNumMap<DiscreteVariable> discreteVariables;

	/**
	 * Create an empty log-linear model builder
	 */
	public LogLinearModelBuilder () {
		super();
		// Track model features / which factors can be trained.
		factorGraph = new FactorGraph();
		features = new FeatureSet();
		logLinearFactors = new ArrayList<LogLinearFactor>();
		discreteVariables = VariableNumMap.emptyMap();
	}

	/**
	 * Get the factor graph being constructed with this builder.
	 * @return
	 */
	public LogLinearModel build() {
		return new LogLinearModel(factorGraph, logLinearFactors, features);
	}

	public int addDiscreteVariable(String name, DiscreteVariable variable) {
		int varNum = factorGraph.addVariable(name, variable);
		discreteVariables = discreteVariables.addMapping(varNum, variable);
		return varNum;
	}

	/**
	 * Add a factor to the Bayes net being constructed.
	 * @param factor
	 */
	public void addFactor(LogLinearFactor factor) {
		logLinearFactors.add(factor);
		factorGraph.addFactor(factor);
	}

	public VariableNumMap<DiscreteVariable> lookupDiscreteVariables(List<String> variableNames) {
		VariableNumMap<Variable> allVars = factorGraph.lookupVariables(variableNames);
		VariableNumMap<DiscreteVariable> enumVars = discreteVariables.intersection(allVars);
		assert enumVars.size() == allVars.size();
		return enumVars;
	}

	/**
	 * Adds a new Factor with log-linear weights connecting the specified variables.
	 */
	public LogLinearFactor addLogLinearFactor(List<String> factorVariables) {
		LogLinearFactor factor = new LogLinearFactor(lookupDiscreteVariables(factorVariables), features);
		addFactor(factor);
		return factor;
	}
}