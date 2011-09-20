package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Converter;

/**
 * A VariableNumMap represents a set of variables in a graphical model.
 * VariableNumMaps are immutable.
 * 
 * @author jayant
 * 
 */
public class VariableNumMap {

  private SortedMap<Integer, Variable> varMap;

  /**
   * Instantiate a VariableNumMap with the specified variables. Each variable is
   * named by both a unique integer id and a (possibly not unique) String name.
   * All three passed in lists must be of the same size.
   * 
   * @param varNums - The unique integer id of each variable
   * @param varNames - The String name of each variable
   * @param vars - The Variable type of each variable
   */
  public VariableNumMap(List<Integer> varNums, List<? extends Variable> vars) {
    Preconditions.checkArgument(varNums.size() == vars.size());
    varMap = new TreeMap<Integer, Variable>();
    for (int i = 0; i < varNums.size(); i++) {
      varMap.put(varNums.get(i), vars.get(i));
    }
  }

  public VariableNumMap(Map<Integer, Variable> varNumMap) {
    varMap = new TreeMap<Integer, Variable>(varNumMap);
  }

  /**
   * Copy constructor.
   * 
   * @param varNumMap
   */
  public VariableNumMap(VariableNumMap varNumMap) {
    this.varMap = new TreeMap<Integer, Variable>(varNumMap.varMap);
  }

  /**
   * Get the number of variable mappings contained in the map.
   * 
   * @return
   */
  public int size() {
    return varMap.size();
  }

  /**
   * Returns true if variableNum is mapped to a variable in this map.
   * 
   * @param variableNum
   * @return
   */
  public boolean contains(int variableNum) {
    return varMap.containsKey(variableNum);
  }

  /**
   * Returns {@code true} if every variable number in {@code variableNums} is
   * mapped to a variable in {@code this} map. Returns {@code true} if
   * {@code variableNums} is empty.
   * 
   * @param variableNums
   * @return
   */
  public boolean containsAll(Collection<Integer> variableNums) {
    for (Integer i : variableNums) {
      if (!contains(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Same as {@link #containsAll(Collection)}, using the variable numbers in the
   * passed map.
   * 
   * @param other
   * @return
   */
  public boolean containsAll(VariableNumMap other) {
    return containsAll(other.getVariableNums());
  }

  /**
   * Returns {@code true} if any variable number in {@code variableNums} is
   * mapped to a variable in {@code this} map. Returns {@code false} if
   * {@code variableNums} is empty.
   * 
   * @param variableNums
   * @return
   */
  public boolean containsAny(Collection<Integer> variableNums) {
    for (Integer i : variableNums) {
      if (contains(i)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Same as {@link #containsAny(Collection)}, using the variable numbers in the
   * passed map.
   * 
   * @param other
   * @return
   */
  public boolean containsAny(VariableNumMap other) {
    return containsAny(other.getVariableNums());
  }

  /**
   * Get the numbers of the variables in this map, in ascending sorted order.
   * 
   * @return
   */
  public List<Integer> getVariableNums() {
    return new ArrayList<Integer>(varMap.keySet());
  }

  /**
   * Get the variable types in this map, ordered by variable index.
   * 
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
   * Get the real variables in this map, ordered by variable index.
   */
  public List<RealVariable> getRealVariables() {
    List<RealVariable> discreteVars = new ArrayList<RealVariable>();
    for (Integer varNum : getVariableNums()) {
      if (getVariable(varNum) instanceof RealVariable) {
        discreteVars.add((RealVariable) getVariable(varNum));
      }
    }
    return discreteVars;
  }

  /**
   * Get the variable referenced by a particular variable number. Throws a
   * KeyError if the variable number is not contained in this map.
   * 
   * @param variableNum
   * @return
   */
  public Variable getVariable(int variableNum) {
    return varMap.get(variableNum);
  }

  /*
   * Ensures that all variable numbers which are shared between other and this
   * are mapped to the same variables.
   */
  private void checkCompatibility(VariableNumMap other) {
    for (Integer key : other.getVariableNums()) {
      if (varMap.containsKey(key)
          && varMap.get(key) != other.varMap.get(key)) {
        throw new IllegalArgumentException(
            "Conflicting number -> variable mapping! This object: "
                + this + " other object: " + other);
      }
    }
  }

  /**
   * Return a VariableNumMap containing all variable numbers shared by both
   * maps.
   * 
   * @param other
   * @return
   */
  public VariableNumMap intersection(VariableNumMap other) {
    checkCompatibility(other);
    return intersection(new HashSet<Integer>(other.getVariableNums()));
  }

  /**
   * Return a VariableNumMap containing all variable numbers shared by
   * varNumsToKeep and this.getVariableNums()
   * 
   * @param varNumsToKeep
   * @return
   */
  public VariableNumMap intersection(Collection<Integer> varNumsToKeep) {
    SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>();
    for (Integer key : varNumsToKeep) {
      if (contains(key)) {
        newVarMap.put(key, varMap.get(key));
      }
    }
    return new VariableNumMap(newVarMap);
  }

  /**
   * Removes all variable mappings whose numbers are in other.
   * 
   * @param varNumsToRemove
   * @return
   */
  public VariableNumMap removeAll(VariableNumMap other) {
    checkCompatibility(other);
    return removeAll(new HashSet<Integer>(other.getVariableNums()));
  }

  /**
   * Removes all variable mappings whose numbers are in varNumsToRemove.
   * 
   * @param varNumsToRemove
   * @return
   */
  public VariableNumMap removeAll(Collection<Integer> varNumsToRemove) {
    SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>(
        varMap);
    Set<Integer> varNumsToRemoveSet = Sets.newHashSet(varNumsToRemove);
    for (Integer key : getVariableNums()) {
      if (varNumsToRemoveSet.contains(key)) {
        newVarMap.remove(key);
      }
    }
    return new VariableNumMap(newVarMap);
  }

  /**
   * Returns a VariableNumMap containing the union of the number->variable
   * mappings from this map and other. The maps may not contain conflicting
   * mappings for any number.
   * 
   * @param other
   * @return
   */
  public VariableNumMap union(VariableNumMap other) {
    checkCompatibility(other);
    SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>(
        varMap);
    for (Integer key : other.getVariableNums()) {
      newVarMap.put(key, other.varMap.get(key));
    }
    return new VariableNumMap(newVarMap);
  }

  /**
   * Adds or replaces a single number/variable mapping in this map.
   * 
   * @param num
   * @param var
   * @return
   */
  public VariableNumMap addMapping(int num, Variable var) {
    SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>(
        varMap);
    newVarMap.put(num, var);
    return new VariableNumMap(newVarMap);
  }

  /**
   * Returns a map with each variable number in {@code this} replaced with its
   * value in {@code numMapping}.
   * 
   * @param numMapping
   * @return
   */
  public VariableNumMap mapVariables(Map<Integer, Integer> numMapping) {
    SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>();
    for (Map.Entry<Integer, Variable> entry : varMap.entrySet()) {
      newVarMap.put(numMapping.get(entry.getKey()), entry.getValue());
    }
    return new VariableNumMap(newVarMap);
  }

  /**
   * Gets the values of the variables in {@code Assignment} and returns them as
   * a {@code List}. This operation is the inverse of
   * {@link #outcomeToAssignment(List)}. The size of the returned list is equal
   * to {@code this.size()}.
   * 
   * @param assignment
   * @return
   */
  public List<Object> assignmentToOutcome(Assignment assignment) {
    List<Object> returnValue = Lists.newArrayList();
    for (Integer varNum : varMap.keySet()) {
      returnValue.add(assignment.getVarValue(varNum));
    }
    return returnValue;
  }

  /**
   * Get the assignment corresponding to a particular setting of the variables
   * in this set. The Objects in outcome are assumed to be ordered in ascending
   * order by variable number. (i.e., the ith object is the value of the ith
   * variable returned by getVariableNums())
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
   * Get the assignment corresponding to a particular setting of the variables
   * in this factor.
   */
  public Assignment outcomeToAssignment(Object[] outcome) {
    return outcomeToAssignment(Arrays.asList(outcome));
  }

  /**
   * Gets a converter for transforming outcomes (settings of variables in
   * {@code this}) into their corresponding assignments, and vice versa. The
   * returned converter performs the functions of
   * {@link #outcomeToAssignment(List)} and
   * {@link #assignmentToOutcome(Assignment)}.
   * 
   * @return
   */
  public Converter<List<Object>, Assignment> getOutcomeToAssignmentConverter() {
    return new AssignmentConverter(this);
  }

  /**
   * Converts an assignment over a set of {@code DiscreteVariable}s into an
   * equivalent {@code int[]} representation. This method is used to efficiently
   * store the possible assignments to discrete variables. The returned array
   * has length equal to {@code this.size()}, and can be converted back into an
   * assignment using {@link #intArrayToAssignment(int[])}.
   * 
   * <p>
   * If {@code assignment} contains values which are not in the domain of the
   * corresponding discrete variables, this method throws an exception.
   * 
   * @param assignment
   * @return
   */
  public int[] assignmentToIntArray(Assignment assignment) {
    int[] value = new int[size()];
    int index = 0;
    for (Map.Entry<Integer, Variable> entry : varMap.entrySet()) {
      if (entry.getValue() instanceof DiscreteVariable) {
        value[index] = ((DiscreteVariable) entry.getValue()).getValueIndex(assignment.getVarValue(entry.getKey()));
      }
      index++;
    }
    return value;
  }

  /**
   * Converts the passed {@code int[]} of variable value indices into an
   * {@code Assignment} by mapping it through the {@code DiscreteVariable}s
   * contained in {@code this}. This operation is the inverse of
   * {@link #assignmentToIntArray(Assignment)}
   * 
   * @param values
   * @return
   */
  public Assignment intArrayToAssignment(int[] values) {
    List<Object> objectValues = Lists.newArrayList();
    int i = 0;
    for (Map.Entry<Integer, Variable> entry : varMap.entrySet()) {
      if (entry.getValue() instanceof DiscreteVariable) {
        objectValues.add(((DiscreteVariable) entry.getValue()).getValue(values[i]));
      }
      i++;
    }
    return new Assignment(getVariableNums(), objectValues);
  }

  /**
   * Returns {@code true} if the values in {@code assignment} are possible
   * values for the variables in {@code this}. {@code assignment} must 
   * contain a subset of the variables in {@code this}.
   * 
   * @param assignment
   * @return
   */
  public boolean isValidAssignment(Assignment assignment) {
    Preconditions.checkArgument(containsAll(assignment.getVarNumsSorted()));
    for (Integer varNum : assignment.getVarNumsSorted()) {
      if (!varMap.get(varNum).canTakeValue(assignment.getVarValue(varNum))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return varMap.values().toString();
  }

  @Override
  public int hashCode() {
    return varMap.hashCode();
  }

  /**
   * VariableNumMaps are equal if they contain exactly the same variable number
   * -> variable mappings.
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof VariableNumMap
        && varMap.equals(((VariableNumMap) o).varMap);
  }

  /**
   * Get a VariableNumMap with no num -> variable mappings.
   */
  public static VariableNumMap emptyMap() {
    List<Variable> empty = Collections.emptyList();
    return new VariableNumMap(Arrays.asList(new Integer[] {}), empty);
  }

  /**
   * Returns the union of all of the passed-in maps, which may not contain
   * conflicting mappings for any variable number.
   * 
   * @param varNumMaps
   * @return
   */
  public static VariableNumMap unionAll(Collection<VariableNumMap> varNumMaps) {
    VariableNumMap curMap = emptyMap();
    for (VariableNumMap varNumMap : varNumMaps) {
      curMap = curMap.union(varNumMap);
    }
    return curMap;
  }

  /**
   * Converter from assignments to outcomes (list of objects) and vice-versa.
   * 
   * @author jayantk
   */
  private class AssignmentConverter extends Converter<List<Object>, Assignment> {

    private final VariableNumMap variables;

    public AssignmentConverter(VariableNumMap variables) {
      this.variables = variables;
    }

    @Override
    public Assignment apply(List<Object> item) {
      Preconditions.checkArgument(item.size() == variables.size());
      return variables.outcomeToAssignment(item);
    }

    @Override
    public List<Object> invert(Assignment item) {
      Preconditions.checkArgument(item.size() == variables.size());
      return variables.assignmentToOutcome(item);
    }
  }
}
