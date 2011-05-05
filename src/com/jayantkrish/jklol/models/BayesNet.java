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
	List<Integer> parentVarNums = new ArrayList<Integer>();
	List<Variable> parentVars = new ArrayList<Variable>();
	lookupVarStrings(parentVariables, parentVarNums, parentVars);
	List<Integer> childVarNums = new ArrayList<Integer>();
	List<Variable> childVars = new ArrayList<Variable>();
	lookupVarStrings(childVariables, childVarNums, childVars);


	List<Integer> allVarNums = new ArrayList<Integer>();
	List<Variable> allVars = new ArrayList<Variable>();
	for (int i = 0; i < parentVarNums.size(); i++) {
	    allVarNums.add(parentVarNums.get(i));
	    allVars.add(parentVars.get(i));
	}
	for (int i = 0; i < childVarNums.size(); i++) {
	    allVarNums.add(childVarNums.get(i));
	    allVars.add(childVars.get(i));
	}

	CptTableFactor factor = new CptTableFactor(allVarNums, allVars, parentVarNums, parentVars, childVarNums, childVars);
	cptFactors.add(factor);
	addFactor(factor);
	return factor;
    }

    /**
     * Adds a new CptTableFactor, like the previous method, except that it also initializes the factor
     * with its own (new) Cpt.
     */
    public CptTableFactor addCptFactorWithNewCpt(List<String> parentVariables, List<String> childVariables) {
	CptTableFactor factor = addCptFactor(parentVariables, childVariables);

	List<Integer> parentVarNums = new ArrayList<Integer>();
	List<Variable> parentVars = new ArrayList<Variable>();
	lookupVarStrings(parentVariables, parentVarNums, parentVars);
	List<Integer> childVarNums = new ArrayList<Integer>();
	List<Variable> childVars = new ArrayList<Variable>();
	lookupVarStrings(childVariables, childVarNums, childVars);

	Cpt cpt = new Cpt(parentVars, childVars);
	Map<Integer, Integer> nodeCptMap = new HashMap<Integer, Integer>();
	for (int i = 0; i < parentVarNums.size(); i++) {
	    nodeCptMap.put(parentVarNums.get(i), i);
	}
	for (int i = 0; i < childVarNums.size(); i++) {
	    nodeCptMap.put(childVarNums.get(i), i + parentVarNums.size());
	}

	factor.setCpt(cpt, nodeCptMap);
	return factor;
    }

    /**
     * Add a new conditional probability factor embedding a context-free grammar.
     */
    public CfgFactor addCfgCptFactor(String parentVarName, String childVarName, 
	    Grammar grammar, CptProductionDistribution productionDist) {
	List<Integer> parentVarNums = new ArrayList<Integer>();
	List<Variable> parentVars = new ArrayList<Variable>();
	lookupVarStrings(Arrays.asList(new String[] {parentVarName}), parentVarNums, parentVars);
	List<Integer> childVarNums = new ArrayList<Integer>();
	List<Variable> childVars = new ArrayList<Variable>();
	lookupVarStrings(Arrays.asList(new String[] {childVarName}), childVarNums, childVars);

	Variable parentVar = parentVars.get(0);
	Variable childVar = childVars.get(0);

	/*
	if (!(parentVar instanceof Variable<Production> && childVar instanceof Variable<Production[]>)) {
	    throw new RuntimeException("Variables must be of type Production and Production[]!");
	}
	*/

	CfgFactor factor = new CfgFactor((Variable<Production>) parentVars.get(0), 
		(Variable<List<Production>>) childVars.get(0), parentVarNums.get(0), 
		childVarNums.get(0), grammar, productionDist);

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