package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.*;

import java.util.*;
import java.io.Serializable;

/**
 * A FactorGraph represents a Markov network.
 */
public class FactorGraph implements Serializable {

    private List<Variable> variables;
    private Map<String, Integer> variableNumMap;
    private HashMultimap<Integer, Integer> variableFactorMap;
    private HashMultimap<Integer, Integer> factorVariableMap;

    private IndexedList<Factor> factors;

    /**
     * Create an empty factor graph.
     */
    public FactorGraph() {
	variables = new ArrayList<Variable>();
	variableNumMap = new HashMap<String, Integer>();
	variableFactorMap = new HashMultimap<Integer, Integer>();
	factorVariableMap = new HashMultimap<Integer, Integer>();
	factors = new IndexedList<Factor>();
    }

    /**
     * Add a new variable (vertex) to the markov network. The variable starts out
     * unconnected to any factors. The method returns the numerical ID of the variable, which are assigned in sorted order.
     */
    public int addVariable(String variableName, Variable variable) {
	int varNum = variables.size();
	variableNumMap.put(variableName, varNum);
	variables.add(variable);

	return varNum;
    }

    /**
     * Adds a new Factor to the Markov network connecting the specified variables.
     */
    public TableFactor addTableFactor(List<String> factorVariables) {
	List<Integer> varNums = new ArrayList<Integer>();
	List<Variable> vars = new ArrayList<Variable>();
	lookupVarStrings(factorVariables, varNums, vars);

	TableFactor factor = new TableFactor(varNums, vars);
	addFactor(factor);
	return factor;
    }

    /**
     * Add a new factor to the model, returning the unique number
     * assigned to it.
     */
    protected int addFactor(Factor factor) {
	int factorNum = factors.size();
	factors.add(factor);

	for (Integer i : factor.getVarNums()) {
	    variableFactorMap.put(i, factorNum);
	    factorVariableMap.put(factorNum, i);
	}
	return factorNum;
    }


    /**
     * Get the number of factors in the graph.
     */ 
    public int numFactors() {
	return factors.size();
    }
    
    /**
     * Get a factor using its index number.
     */
    public Factor getFactorFromIndex(int factorNum) {
	return factors.get(factorNum);
    }

    /**
     * Get the index of a factor.
     */
    public int getFactorIndex(Factor f) {
	return factors.getIndex(f);
    }

    /**
     * Get all factors.
     */
    public List<Factor> getFactors() {
	return factors.items();
    }

    /**
     * Get a variable using its index number.
     */
    public Variable getVariableFromIndex(int varNum) {
	return variables.get(varNum);
    }

    /**
     * Get the index of a variable from its name.
     */
    public int getVariableIndex(String variableName) {
	return variableNumMap.get(variableName);
    }

    /**
     * Get the variables in the factor graph.
     */
    public List<Variable> getVariables() {
	return Collections.unmodifiableList(variables);
    }

    /**
     * Get an iterator over all possible assignments to a set of variables.
     */
    public Iterator<Assignment> assignmentIterator(List<String> factorVariables) {
	List<Integer> varNums = new ArrayList<Integer>();
	List<Variable> vars = new ArrayList<Variable>();
	lookupVarStrings(factorVariables, varNums, vars);

	return new AllAssignmentIterator(varNums, vars);
    }


    /**
     * Get an assignment for the named set of variables.
     */
    public Assignment outcomeToAssignment(String[] factorVariables, Object[] outcome) {
	return outcomeToAssignment(Arrays.asList(factorVariables), Arrays.asList(outcome));
    }

    /**
     * Get an assignment for the named set of variables.
     */
    public Assignment outcomeToAssignment(List<String> factorVariables, List<? extends Object> outcome) {
	assert factorVariables.size() == outcome.size();

	List<Integer> varNums = new ArrayList<Integer>(factorVariables.size());
	List<Integer> outcomeValueInds = new ArrayList<Integer>(outcome.size());
	for (int i = 0; i < factorVariables.size(); i++) {
	    int varInd = getVariableIndex(factorVariables.get(i));
	    varNums.add(varInd);	    
	    outcomeValueInds.add(variables.get(varInd).getValueIndexObject(outcome.get(i)));
	}
	return new Assignment(varNums, outcomeValueInds);
    }

    public Map<String, Object> assignmentToObject(Assignment a) {
	Map<String, Object> objectVals = new HashMap<String, Object>();
	for (String varName : variableNumMap.keySet()) {
	    int varNum = variableNumMap.get(varName);
	    if (a.containsVar(varNum)) {
		objectVals.put(varName, variables.get(varNum).getValue(a.getVarValue(varNum)));
	    }
	}
	return objectVals;
    }

    /**
     * Get all of the factors which contain the passed-in varNum
     */
    public Set<Integer> getFactorsWithVariable(int varNum) {
	return variableFactorMap.get(varNum);
    }


    /**
     * Get all of the variables that the two factors have in common.
     */ 
    public Set<Integer> getSharedVariables(int factor1, int factor2) {
	Set<Integer> varNums = new HashSet<Integer>(factorVariableMap.get(factor1));
	varNums.retainAll(factorVariableMap.get(factor2));
	return varNums;
    }

    /*
     * Get the variable numbers and variables corresponding to a particular set of variable
     * names. The resulting values are appended to varNums and vars, respectively.
     */
    protected void lookupVarStrings(List<String> factorVariables, 
	    List<Integer> varNums, List<Variable> vars) {
	for (String variableName : factorVariables) {
	    if (!variableNumMap.containsKey(variableName)) {
		throw new RuntimeException("Must use an already specified variable name.");
	    }
	    varNums.add(variableNumMap.get(variableName));
	    vars.add(variables.get(variableNumMap.get(variableName)));
	}
    }
}