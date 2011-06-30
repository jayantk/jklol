package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayantkrish.jklol.cfg.CfgFactor;
import com.jayantkrish.jklol.cfg.CptProductionDistribution;
import com.jayantkrish.jklol.cfg.Grammar;
import com.jayantkrish.jklol.models.factors.CptFactor;
import com.jayantkrish.jklol.models.factors.CptTableFactor;

/**
 * A BayesNetBuilder provides methods for constructing a BayesNet 
 */
public class BayesNetBuilder {

	private FactorGraph bayesNet;
	private List<CptFactor<?>> cptFactors;
	private VariableNumMap<DiscreteVariable> discreteVars;

	public BayesNetBuilder() {
		bayesNet = new FactorGraph();
		cptFactors = new ArrayList<CptFactor<?>>();
		discreteVars = VariableNumMap.emptyMap();
	}

	/**
	 * Get the factor graph being constructed with this builder.
	 * @return
	 */
	public BayesNet build() {
		return new BayesNet(bayesNet, cptFactors);
	}
	
	/**
	 * Add a variable to the bayes net.
	 */
	public int addDiscreteVariable(String name, DiscreteVariable variable) {
		int varNum = bayesNet.addVariable(name, variable);
		discreteVars = discreteVars.addMapping(varNum, variable);
		return varNum;
	}
	
	public VariableNumMap<DiscreteVariable> lookupDiscreteVariables(List<String> variableNames) {
		VariableNumMap<Variable> allVars = bayesNet.lookupVariables(variableNames);
		VariableNumMap<DiscreteVariable> enumVars = discreteVars.intersection(allVars);
		assert enumVars.size() == allVars.size();
		return enumVars;
	}

	/**
	 * Add a factor to the Bayes net being constructed.
	 * @param factor
	 */
	public void addFactor(CptFactor<?> factor) {
		cptFactors.add(factor);
		bayesNet.addFactor(factor);
	}

	/**
	 * Adds a new conditional probability distribution over the child variables conditioned on the
	 * parents.
	 *
	 * The conditional probability table for the CptTableFactor comes uninitialized and must be initialized
	 * by the caller.
	 */
	public CptTableFactor addCptFactor(List<String> parentVariables, List<String> childVariables) {
		CptTableFactor factor = new CptTableFactor(lookupDiscreteVariables(parentVariables), 
				lookupDiscreteVariables(childVariables));
		addFactor(factor);
		return factor;
	}

	/**
	 * Adds a new CptTableFactor, like the previous method, except that it also initializes the factor
	 * with its own (new) Cpt.
	 */
	public CptTableFactor addCptFactorWithNewCpt(List<String> parentVariables, List<String> childVariables) {
		return addCptFactorWithNewCpt(parentVariables, childVariables, false);
	}

	/**
	 * Adds a new CptTableFactor, like the previous method, except that it also initializes the factor
	 * with its own (new) Cpt. If isSparse, then the factor is initialized with a sparse CPT (that must be
	 * initialized by the caller)!
	 */
	public CptTableFactor addCptFactorWithNewCpt(List<String> parentVariableNames, 
			List<String> childVariableNames, boolean isSparse) {
		CptTableFactor factor = addCptFactor(parentVariableNames, childVariableNames);
		VariableNumMap<DiscreteVariable> parentVars = lookupDiscreteVariables(parentVariableNames);
		VariableNumMap<DiscreteVariable> childVars = lookupDiscreteVariables(childVariableNames);

		Cpt cpt = null;
		if (isSparse) {
			cpt = new SparseCpt(parentVars.getVariables(), childVars.getVariables());
		} else {
			cpt = new Cpt(parentVars.getVariables(), childVars.getVariables());
		}

		Map<Integer, Integer> nodeCptMap = new HashMap<Integer, Integer>();
		for (int i = 0; i < parentVars.getVariableNums().size(); i++) {
			nodeCptMap.put(parentVars.getVariableNums().get(i), i);
		}
		for (int i = 0; i < childVars.getVariableNums().size(); i++) {
			nodeCptMap.put(childVars.getVariableNums().get(i), 
					i + parentVars.getVariableNums().size());
		}

		factor.setCpt(cpt, nodeCptMap);
		return factor;
	}

	/**
	 * Add a new conditional probability factor embedding a context-free grammar.
	 */
	public CfgFactor addCfgCptFactor(String parentVarName, String childVarName, 
			Grammar grammar, CptProductionDistribution productionDist) {
		VariableNumMap<DiscreteVariable> parentVars = lookupDiscreteVariables(Arrays.asList(new String[] {parentVarName}));
		VariableNumMap<DiscreteVariable> childVars = lookupDiscreteVariables(Arrays.asList(new String[] {childVarName}));

		CfgFactor factor = new CfgFactor(parentVars.getVariables().get(0), 
				childVars.getVariables().get(0), parentVars.getVariableNums().get(0), 
				childVars.getVariableNums().get(0), grammar, productionDist);

		addFactor(factor);
		return factor;
	}
}