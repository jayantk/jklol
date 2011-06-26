package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * An Assignment represents a set of values assigned to a set of variables.
 */ 
public class Assignment {

	public static final Assignment EMPTY = new Assignment(Arrays.asList(new Integer[] {}),
			Arrays.asList(new Integer[] {}));

	private SortedMap<Integer, Integer> varValueMap;

	public Assignment(List<Integer> varNums, List<Integer> valueNums) {
		assert varNums.size() == valueNums.size();
		varValueMap = new TreeMap<Integer, Integer>();
		for (int i = 0; i < varNums.size(); i++) {
			varValueMap.put(varNums.get(i), valueNums.get(i));
		}
	}

	public Assignment(int varNum, int valueNum) {
		varValueMap = new TreeMap<Integer, Integer>();
		varValueMap.put(varNum, valueNum);
	}

	public Assignment(Map<Integer, Integer> varValues) {
		varValueMap = new TreeMap<Integer, Integer>(varValues);    	
	}

	/**
	 * Copy constructor
	 */
	public Assignment(Assignment a) {
		varValueMap = new TreeMap<Integer, Integer>(a.varValueMap);
	}

	public List<Integer> getVarNumsSorted() {
		return new ArrayList<Integer>(varValueMap.keySet());
	}

	public List<Integer> getVarValuesInKeyOrder() {
		return new ArrayList<Integer>(varValueMap.values());
	}

	public int getVarValue(int varNum) {
		return varValueMap.get(varNum);
	}

	public boolean containsVar(int varNum) {
		return varValueMap.containsKey(varNum);
	}

	public void setVarValue(int varNum, int value) {
		assert varValueMap.containsKey(varNum);
		varValueMap.put(varNum, value);
	}

	/**
	 * If varNums is a subset of the variables in this assignment, this method returns the value
	 * assigned to each variable in varNums.
	 *
	 * Puts the return value into "returnValue" if it is non-null, otherwise allocates and returns a
	 * new list.
	 */
	public List<Integer> subAssignment(List<Integer> varNums,
			List<Integer> returnValue) {
		List<Integer> retVal = returnValue;
		if (retVal == null) {
			retVal = new ArrayList<Integer>();
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
		List<Integer> retVal = new ArrayList<Integer>();
		for (Integer varNum : varNumList) {
			if (varValueMap.containsKey(varNum)) {
				retVal.add(varValueMap.get(varNum));
			}
		}
		assert retVal.size() == varNums.size();

		return new Assignment(varNumList, retVal);
	}

	public Assignment subAssignment(VariableNumMap vars) {
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
		List<Integer> otherVals = other.getVarValuesInKeyOrder();
		List<Integer> myVals = getVarValuesInKeyOrder();

		List<Integer> mergedNums = new ArrayList<Integer>();
		List<Integer> mergedVals = new ArrayList<Integer>();

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
		SortedMap<Integer, Integer> newVarValueMap = new TreeMap<Integer, Integer>(varValueMap);
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
		List<Integer> newVarVals = new ArrayList<Integer>();
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