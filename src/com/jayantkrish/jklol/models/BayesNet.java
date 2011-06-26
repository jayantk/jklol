package com.jayantkrish.jklol.models;

import java.util.*;

import com.jayantkrish.jklol.cfg.*;

/**
 * A BayesNet represents a bayesian network.
 */
public class BayesNet extends FactorGraph {

    private List<CptFactor> cptFactors;

    public BayesNet() {
	super();
	cptFactors = new ArrayList<CptFactor>();
    }

    /**
     * Adds a new conditional probability distribution over the child variables conditioned on the
     * parents.
     *
     * The conditional probability table for the CptTableFactor comes uninitialized and must be initialized
     * by the caller.
     */
    public CptTableFactor addCptFactor(List<String> parentVariables, List<String> childVariables) {
	CptTableFactor factor = new CptTableFactor(lookupVarStrings(parentVariables), 
			lookupVarStrings(childVariables));
	cptFactors.add(factor);
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
	VariableNumMap parentVars = lookupVarStrings(parentVariableNames);
	VariableNumMap childVars = lookupVarStrings(childVariableNames);

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
	VariableNumMap parentVars = lookupVarStrings(Arrays.asList(new String[] {parentVarName}));
	VariableNumMap childVars = lookupVarStrings(Arrays.asList(new String[] {childVarName}));

	CfgFactor factor = new CfgFactor((Variable<Production>) parentVars.getVariables().get(0), 
		(Variable<List<Production>>) childVars.getVariables().get(0), parentVars.getVariableNums().get(0), 
		childVars.getVariableNums().get(0), grammar, productionDist);

	cptFactors.add(factor);
	addFactor(factor);
	return factor;
    }

    /**
     * Get the CPT factors in the BN.
     */
    public List<CptFactor> getCptFactors() {
	return Collections.unmodifiableList(cptFactors);
    }


}