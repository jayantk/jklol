package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.jayantkrish.jklol.util.Assignment;

/**
 * A VariableNumMap represents a set of variables in a graphical model. 
 * VariableNumMaps are immutable.
 * 
 * @author jayant
 *
 */
public class VariableNumMap {

	private SortedMap<Integer, Variable> varMap;

	public VariableNumMap(List<Integer> varNums, List<? extends Variable> vars) {
		assert varNums.size() == vars.size();
		varMap = new TreeMap<Integer, Variable>();
		for (int i = 0; i < varNums.size(); i++) {
			varMap.put(varNums.get(i), vars.get(i));				
		}		
	}

	public VariableNumMap(Map<Integer, Variable> varNumMap) {
		varMap = new TreeMap<Integer, Variable>(varNumMap);
	}

	/**
	 * Get the number of variable mappings contained in the map. 
	 * @return
	 */
	public int size() {
		return varMap.size();
	}

	/**
	 * Returns true if variableNum is mapped to a variable in this map.
	 * @param variableNum
	 * @return
	 */
	public boolean containsVariableNum(int variableNum) {
		return varMap.containsKey(variableNum);
	}

	/**
	 * Get the numbers of the variables in this map, in ascending sorted order.
	 * @return
	 */
	public List<Integer> getVariableNums() {
		return new ArrayList<Integer>(varMap.keySet());
	}

	/**
	 * Get the variable types in this map, ordered by variable index. 
	 * @return
	 */
	public List<Variable> getVariables() {
		return new ArrayList<Variable>(varMap.values());
	}
	
	/**
	 * Get the discrete variables in this map, ordered by variable index.
	 */
	public List<DiscreteVariable> getDiscreteVariables() {
		List<DiscreteVariable> discreteVars = new ArrayList<DiscreteVariable>();
		for (Integer varNum : getVariableNums()) {
			if (getVariable(varNum) instanceof DiscreteVariable) {
				discreteVars.add((DiscreteVariable) getVariable(varNum));
			}
		}
		return discreteVars;
	}

	/**
	 * Get the variable referenced by a particular variable number. Throws a KeyError if
	 * the variable number is not contained in this map.
	 * @param variableNum
	 * @return
	 */
	public Variable getVariable(int variableNum) {
		return varMap.get(variableNum);
	}

	/*
	 * Ensures that all variable numbers which are shared between other and this are
	 * mapped to the same variables.  
	 */
	private void checkCompatibility(VariableNumMap other) {
		for (Integer key : other.getVariableNums()) {
			if (varMap.containsKey(key) && varMap.get(key) != other.varMap.get(key)) {
				throw new IllegalArgumentException("Conflicting number -> variable mapping! This object: " + 
						this + " other object: " + other);
			} 
		}
	}

	/**
	 * Return a VariableNumMap containing all variable numbers shared 
	 * by both maps.
	 * @param other
	 * @return
	 */
	public VariableNumMap intersection(VariableNumMap other) {
		checkCompatibility(other);
		return intersection(new HashSet<Integer>(other.getVariableNums()));
	}

	/**
	 * Return a VariableNumMap containing all variable numbers shared by varNumsToKeep and
	 * this.getVariableNums() 
	 * @param varNumsToKeep
	 * @return
	 */
	public VariableNumMap intersection(Set<Integer> varNumsToKeep) {
		SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>(varMap);
		for (Integer key : getVariableNums()) {
			if (!varNumsToKeep.contains(key)) {
				newVarMap.remove(key);
			}
		}
		return new VariableNumMap(newVarMap);		
	}

	/**
	 * Removes all variable mappings whose numbers are in other.
	 * @param varNumsToRemove
	 * @return
	 */
	public VariableNumMap removeAll(VariableNumMap other) {
		checkCompatibility(other);
		return removeAll(new HashSet<Integer>(other.getVariableNums()));
	}

	/**
	 * Removes all variable mappings whose numbers are in varNumsToRemove.
	 * @param varNumsToRemove
	 * @return
	 */
	public VariableNumMap removeAll(Set<Integer> varNumsToRemove) {
		SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>(varMap);
		for (Integer key : getVariableNums()) {
			if (varNumsToRemove.contains(key)) {
				newVarMap.remove(key);
			}
		}
		return new VariableNumMap(newVarMap);
	}

	/**
	 * Returns a VariableNumMap containing the union of the number->variable mappings from 
	 * this map and other. The maps may not contain conflicting mappings for any number.
	 * @param other
	 * @return
	 */
	public VariableNumMap union(VariableNumMap other) {
		checkCompatibility(other);
		SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>(varMap);
		for (Integer key : other.getVariableNums()) { 
			newVarMap.put(key, other.varMap.get(key));
		}
		return new VariableNumMap(newVarMap);
	}

	/**
	 * Adds or replaces a number -> variable mapping from the map.  
	 * @param num
	 * @param var
	 * @return
	 */
	public VariableNumMap addMapping(int num, Variable var) {
		SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>(varMap);
		newVarMap.put(num, var);
		return new VariableNumMap(newVarMap);
	}

	/**
	 * Get the assignment corresponding to a particular setting of the variables in this set.
	 * The Objects in outcome are assumed to be ordered in ascending order by variable number.
	 * (i.e., the ith object is the value of the ith variable returned by getVariableNums()) 
	 */
	public Assignment outcomeToAssignment(List<? extends Object> outcome) {
		assert outcome.size() == varMap.size();

		Map<Integer, Object> varValueMap = new HashMap<Integer, Object>();
		int i = 0;
		for (Map.Entry<Integer, Variable> varIndex : varMap.entrySet()) {
			assert varIndex.getValue().canTakeValue(outcome.get(i));
			varValueMap.put(varIndex.getKey(), outcome.get(i));
			i++;
		}

		return new Assignment(varValueMap);
	}

	/**
	 * Get the assignment corresponding to a particular setting of the variables in this factor.
	 */
	public Assignment outcomeToAssignment(Object[] outcome) {
		return outcomeToAssignment(Arrays.asList(outcome));
	}

	/**
	 * VariableNumMaps are equal if they contain exactly the same variable number -> variable mappings.  	
	 */
	public boolean equals(Object o) {
		return o instanceof VariableNumMap && varMap.equals(((VariableNumMap) o).varMap);
	}

	/**
	 * Get a VariableNumMap with no num -> variable mappings.
	 */
	public static VariableNumMap emptyMap() {
		List<Variable> empty = Collections.emptyList();
		return new VariableNumMap(Arrays.asList(new Integer[] {}), empty);
	}
}
