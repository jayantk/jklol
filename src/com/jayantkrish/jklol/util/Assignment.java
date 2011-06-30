package com.jayantkrish.jklol.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * An Assignment represents a set of values assigned to a set of variables.
 * Assignments are immutable.
 */ 
public class Assignment { 

	public static final Assignment EMPTY = new Assignment(Arrays.asList(new Integer[] {}),
			Arrays.asList(new Object[] {}));

	private SortedMap<Integer, Object> varValueMap;

	public Assignment(List<Integer> varNums, List<? extends Object> values) {
		// TODO: Store actual variables (VariableNumMap) and ensure that 
		// assigned values are compatible.
		assert varNums.size() == values.size();
		varValueMap = new TreeMap<Integer, Object>();
		for (int i = 0; i < varNums.size(); i++) {
			varValueMap.put(varNums.get(i), values.get(i));
		}
	}

	public Assignment(int varNum, Object value) {
		varValueMap = new TreeMap<Integer, Object>();
		varValueMap.put(varNum, value);
	}

	public Assignment(Map<Integer, Object> varValues) {
		varValueMap = new TreeMap<Integer, Object>(varValues);    	
	}

	/**
	 * Copy constructor
	 */
	public Assignment(Assignment a) {
		varValueMap = new TreeMap<Integer, Object>(a.varValueMap);
	}

	public List<Integer> getVarNumsSorted() {
		return new ArrayList<Integer>(varValueMap.keySet());
	}

	public List<Object> getVarValuesInKeyOrder() {
		return new ArrayList<Object>(varValueMap.values());
	}

	public Object getVarValue(int varNum) {
		return varValueMap.get(varNum);
	}

	public boolean containsVar(int varNum) {
		return varValueMap.containsKey(varNum);
	}

	// TODO(jayantk): Delete the shit out of this method!!
	/*
	public void setVarValue(int varNum, Object value) {
		assert varValueMap.containsKey(varNum);
		varValueMap.put(varNum, value);
	}
	 */

	/**
	 * If varNums is a subset of the variables in this assignment, this method returns the value
	 * assigned to each variable in varNums.
	 *
	 * Puts the return value into "returnValue" if it is non-null, otherwise allocates and returns a
	 * new list.
	 */
	public List<Object> subAssignment(List<Integer> varNums,
			List<Object> returnValue) {
		List<Object> retVal = returnValue;
		if (retVal == null) {
			retVal = new ArrayList<Object>();
		}

		for (Integer varNum : varNums) {
			retVal.add(varValueMap.get(varNum));
		}
		return retVal;
	}

	/**
	 * If varNums is a subset of the variables in this assignment, this method returns the value
	 * assigned to each variable in varNums. 
	 *
	 * varNums may not contain indices which are not represented in this assignment. 
	 */
	public Assignment subAssignment(Collection<Integer> varNums) {
		List<Integer> varNumList = new ArrayList<Integer>(varNums);
		List<Object> retVal = new ArrayList<Object>();
		for (Integer varNum : varNumList) {
			if (varValueMap.containsKey(varNum)) {
				retVal.add(varValueMap.get(varNum));
			}
		}
		assert retVal.size() == varNums.size();
		return new Assignment(varNumList, retVal);
	}

	public Assignment subAssignment(VariableNumMap<?> vars) {
		return subAssignment(vars.getVariableNums());
	}

	/**
	 * Combines two assignments into a single joint assignment to
	 * all of the variables in each assignment. The two assignments
	 * must contain disjoint sets of variables.
	 */
	public Assignment jointAssignment(Assignment other) {

		// Merge varnums / values
		List<Integer> otherNums = other.getVarNumsSorted();
		List<Integer> myNums = getVarNumsSorted();
		List<Object> otherVals = other.getVarValuesInKeyOrder();
		List<Object> myVals = getVarValuesInKeyOrder();

		List<Integer> mergedNums = new ArrayList<Integer>();
		List<Object> mergedVals = new ArrayList<Object>();

		int i = 0;
		int j = 0;
		while (i < otherNums.size() && j < myNums.size()) {
			if (otherNums.get(i) < myNums.get(j)) {
				mergedNums.add(otherNums.get(i));
				mergedVals.add(otherVals.get(i));
				i++;
			} else if (otherNums.get(i) > myNums.get(j)) {
				mergedNums.add(myNums.get(j));
				mergedVals.add(myVals.get(j));
				j++;
			} else {
				throw new RuntimeException("Cannot combine non-disjoint assignments");
			}
		}
		// One list might still have elements in it.
		while (i < otherNums.size()) {
			mergedNums.add(otherNums.get(i));
			mergedVals.add(otherVals.get(i));
			i++;
		}
		while (j < myNums.size()) {
			mergedNums.add(myNums.get(j));
			mergedVals.add(myVals.get(j));
			j++;
		}

		return new Assignment(mergedNums, mergedVals);
	}

	/**
	 * Returns a copy of this assignment without any assignments to the variable 
	 * numbers in varNumsToRemove 
	 * @param varNumsToRemove
	 * @return
	 */
	public Assignment removeAll(Collection<Integer> varNumsToRemove) {
		SortedMap<Integer, Object> newVarValueMap = new TreeMap<Integer, Object>(varValueMap);
		for (Integer varNum : varNumsToRemove) {
			if (newVarValueMap.containsKey(varNum)) {
				newVarValueMap.remove(varNum);
			}
		}
		return new Assignment(newVarValueMap);
	}

	/**
	 * Return a new assignment where each var num has been replaced by its value in 
	 * varMap.
	 */
	public Assignment mappedAssignment(Map<Integer, Integer> varMap) {
		List<Integer> newVarNums = new ArrayList<Integer>();
		List<Object> newVarVals = new ArrayList<Object>();
		for (Integer k : varValueMap.keySet()) {
			if (varMap.containsKey(k)) {
				newVarNums.add(varMap.get(k));
				newVarVals.add(varValueMap.get(k));
			}
		}
		return new Assignment(newVarNums, newVarVals);
	}

	public int hashCode() {
		return varValueMap.hashCode();
	}

	public boolean equals(Object o) {
		if (o instanceof Assignment) {
			Assignment a = (Assignment) o;
			return varValueMap.equals(a.varValueMap);
		}
		return false;
	}

	public String toString() {
		return varValueMap.keySet().toString() + "=" + varValueMap.values().toString();
	}
}