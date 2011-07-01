package com.jayantkrish.jklol.models.loglinear;

import java.util.ArrayList;
import java.util.List;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * A Markov Network with log-linear parameter weights, parameterized by an arbitrary set of
 * (clique-factored) feature functions.
 */ 
public class LogLinearModelBuilder {

	private FactorGraph factorGraph;	
	private List<DiscreteLogLinearFactor> logLinearFactors;
	private LogLinearParameters features;

	private VariableNumMap discreteVariables;

	/**
	 * Create an empty log-linear model builder
	 */
	public LogLinearModelBuilder () {
		super();
		// Track model features / which factors can be trained.
		factorGraph = new FactorGraph();
		features = new LogLinearParameters();
		logLinearFactors = new ArrayList<DiscreteLogLinearFactor>();
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
	public void addFactor(DiscreteLogLinearFactor factor) {
		logLinearFactors.add(factor);
		factorGraph.addFactor(factor);
	}

	/**
	 * Adds a new Factor with log-linear weights connecting the specified variables.
	 */
	public DiscreteLogLinearFactor addLogLinearFactor(List<String> factorVariables) {
		DiscreteLogLinearFactor factor = new DiscreteLogLinearFactor(factorGraph.lookupVariables(factorVariables), features);
		addFactor(factor);
		return factor;
	}
}