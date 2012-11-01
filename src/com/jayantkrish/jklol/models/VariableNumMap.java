package com.jayantkrish.jklol.models;

import java.io.Serializable;
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
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Converter;

/**
 * A {@code VariableNumMap} represents a set of variables in a graphical model.
 * Each variable has a unique numerical index, a unique name, and a
 * {@code Variable} representing the type of its values. {@code VariableNumMap}s
 * are immutable.
 * 
 * @author jayant
 */
public class VariableNumMap implements Serializable {

  private static final long serialVersionUID = 4365097309859003264L;

  private final SortedMap<Integer, Variable> varMap;
  private final BiMap<Integer, String> names;

  /**
   * Instantiate a VariableNumMap with the specified variables. Each variable is
   * named by both a unique integer id and a (possibly not unique) String name.
   * All three passed in lists must be of the same size.
   * 
   * @param varNums - The unique integer id of each variable
   * @param varNames - The String name of each variable
   * @param vars - The Variable type of each variable
   */
  public VariableNumMap(List<Integer> varNums, List<String> varNames, List<? extends Variable> vars) {
    Preconditions.checkArgument(varNums.size() == vars.size());
    Preconditions.checkArgument(varNums.size() == varNames.size());
    varMap = new TreeMap<Integer, Variable>();
    names = HashBiMap.create();
    for (int i = 0; i < varNums.size(); i++) {
      varMap.put(varNums.get(i), vars.get(i));
      names.put(varNums.get(i), varNames.get(i));
    }
  }

  /**
   * Constructor used internally for building the results of operations that
   * return new {@code VariableNumMap}s, such as {@link #union(VariableNumMap)}.
   * 
   * @param varNumMap
   * @param varNames
   */
  private VariableNumMap(SortedMap<Integer, Variable> varNumMap,
      BiMap<Integer, String> varNames) {
    varMap = varNumMap;
    names = varNames;
  }

  public static VariableNumMap fromVariableNames(List<String> variableNames,
      List<Variable> variables) {
    Preconditions.checkArgument(variableNames.size() == variables.size());
    List<Integer> varNums = Lists.newArrayList();
    for (int i = 0; i < variables.size(); i++) {
      varNums.add(i);
    }
    return new VariableNumMap(varNums, variableNames, variables);
  }

  /**
   * Creates a {@code VariableNumMap} containing a single variable.
   * 
   * @param varNum
   * @param varName
   * @param variable
   * @return
   */
  public static VariableNumMap singleton(int varNum, String varName, Variable variable) {
    return new VariableNumMap(Ints.asList(varNum), Arrays.asList(varName), Arrays.asList(variable));
  }

  /**
   * Converts a list of outcomes into a list of assignments. Each row of
   * {@code outcomes} is converted into an assignment by mapping the ith column
   * of {@code outcomes[i]} to an assignment of the ith variable in
   * {@code variables}. {@code null} entries are ignored.
   * 
   * @param outcomes
   * @return
   */
  public static List<Assignment> outcomeTableToAssignment(Object[][] outcomes,
      List<VariableNumMap> variables) {
    List<Assignment> assignments = Lists.newArrayList();
    for (int i = 0; i < outcomes.length; i++) {
      Assignment currentAssignment = Assignment.EMPTY;
      for (int j = 0; j < outcomes[i].length; j++) {
        if (outcomes[i][j] != null) {
          currentAssignment = currentAssignment.union(variables.get(j).outcomeArrayToAssignment(outcomes[i][j]));
        }
      }
      assignments.add(currentAssignment);
    }
    return assignments;
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
   * Get the numbers of the variables in this map, in ascending sorted order.
   * 
   * @return
   */
  public List<Integer> getVariableNums() {
    return new ArrayList<Integer>(varMap.keySet());
  }

  /**
   * Get the numbers of the variables in this map as an array, in ascending
   * sorted order.
   * 
   * @return
   */
  public int[] getVariableNumsArray() {
    return Ints.toArray(varMap.keySet());
  }

  /**
   * Gets the number of the sole variable contained in {@code this}. Requires
   * {@code this.size() == 1}.
   * 
   * @return
   */
  public int getOnlyVariableNum() {
    Preconditions.checkState(varMap.size() == 1);
    return varMap.keySet().iterator().next();
  }

  public String getOnlyVariableName() {
    Preconditions.checkState(names.size() == 1);
    return names.values().iterator().next();
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
   * Gets the {@code Variable} of the sole variable contained in {@code this}.
   * Requires {@code this.size() == 1}.
   * 
   * @return
   */
  public Variable getOnlyVariable() {
    Preconditions.checkState(varMap.size() == 1);
    return varMap.values().iterator().next();
  }

  /**
   * Gets the names of all of the variables in {@code this}, ordered by their
   * variable index.
   * 
   * @return
   */
  public List<String> getVariableNames() {
    List<String> orderedNames = Lists.newArrayList();
    for (Integer variableNum : varMap.keySet()) {
      orderedNames.add(names.get(variableNum));
    }
    return orderedNames;
  }

  /**
   * Gets the name of the variable whose numerical index is {@code index}.
   * Throws a {@code KeyError} if no such variable exists.
   * 
   * @return
   */
  public String getVariableNameFromIndex(int index) {
    return names.get(index);
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
   * Gets an array containing the number of possible values for each variable in
   * this. Requires all {@code Variable}s in this to be {@code DiscreteVariable}
   * s. The returned size array is sorted by dimension number.
   * 
   * @return
   */
  public int[] getVariableSizes() {
    int[] sizes = new int[size()];
    List<DiscreteVariable> varTypes = getDiscreteVariables();
    for (int i = 0; i < varTypes.size(); i++) {
      sizes[i] = varTypes.get(i).numValues();
    }
    return sizes;
  }

  /**
   * Gets an array containing the number of possible joint assignments to the
   * variables in {@code this}. Requires all {@code Variable}s in this to be
   * {@code DiscreteVariable}s.
   * 
   * @return
   */
  public int getNumberOfPossibleAssignments() {
    int[] sizes = getVariableSizes();
    int numAssignments = 1;
    for (int i = 0; i < sizes.length; i++) {
      numAssignments *= sizes[i];
    }
    return numAssignments;
  }

  /**
   * Get the real variables in this map, ordered by variable index.
   */
  public List<RealVariable> getRealVariables() {
    List<RealVariable> realVars = new ArrayList<RealVariable>();
    for (Integer varNum : getVariableNums()) {
      if (getVariable(varNum) instanceof RealVariable) {
        realVars.add((RealVariable) getVariable(varNum));
      }
    }
    return realVars;
  }

  /**
   * Gets any variables in {@code this} whose values are objects of type
   * {@code T}.
   * 
   * @return
   */
  public List<ObjectVariable> getObjectVariables() {
    List<ObjectVariable> integerVars = new ArrayList<ObjectVariable>();
    for (Integer varNum : getVariableNums()) {
      if (getVariable(varNum) instanceof ObjectVariable) {
        integerVars.add((ObjectVariable) getVariable(varNum));
      }
    }
    return integerVars;
  }

  /**
   * Gets any boolean-valued variables in {@code this}.
   * 
   * @return
   */
  public List<BooleanVariable> getBooleanVariables() {
    List<BooleanVariable> booleanVars = new ArrayList<BooleanVariable>();
    for (int varNum : getVariableNums()) {
      if (getVariable(varNum) instanceof BooleanVariable) {
        booleanVars.add((BooleanVariable) getVariable(varNum));
      }
    }
    return booleanVars;
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

  /**
   * Gets the index of the variable named {@code variableName}. Throws a
   * {@code KeyError} if no such variable exists.
   * 
   * @param variableName
   * @return
   */
  public int getVariableByName(String variableName) {
    return names.inverse().get(variableName);
  }

  /**
   * Gets the subset of {@code this} containing variables with a name in
   * {@code variableNames}. Names in {@code variableNames} which are not in
   * {@code this} are ignored.
   * 
   * @param variableNames
   * @return
   */
  public VariableNumMap getVariablesByName(Collection<String> variableNames) {
    BiMap<String, Integer> nameIndex = names.inverse();
    BiMap<Integer, String> newNames = HashBiMap.create();
    SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>();
    for (String name : variableNames) {
      if (nameIndex.containsKey(name)) {
        int currentNameIndex = nameIndex.get(name);
        newNames.put(currentNameIndex, name);
        newVarMap.put(currentNameIndex, varMap.get(currentNameIndex));
      }
    }
    return new VariableNumMap(newVarMap, newNames);
  }

  /**
   * Identical to {@link #getVariablesByName(Collection)}, but with an array of
   * names instead of a {@code Collection}.
   * 
   * @param variableNames
   * @return
   */
  public VariableNumMap getVariablesByName(String... variableNames) {
    return getVariablesByName(Arrays.asList(variableNames));
  }

  /**
   * Gets all variables whose names begin with {@code namePrefix}.
   * 
   * @param namePrefix
   * @return
   */
  public VariableNumMap getVariablesByNamePrefix(String namePrefix) {
    BiMap<Integer, String> newNames = HashBiMap.create();
    SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>();

    for (Map.Entry<Integer, String> varName : names.entrySet()) {
      if (varName.getValue().startsWith(namePrefix)) {
        newNames.put(varName.getKey(), varName.getValue());
        newVarMap.put(varName.getKey(), varMap.get(varName.getKey()));
      }
    }
    return new VariableNumMap(newVarMap, newNames);
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
   * Returns {@code true} if {@code this} contains a variable named
   * {@code variableName}.
   * 
   * @param variableName
   * @return
   */
  public boolean contains(String variableName) {
    return names.inverse().containsKey(variableName);
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

  /*
   * Ensures that all variable numbers which are shared between other and this
   * are mapped to the same variables.
   */
  private void checkCompatibility(VariableNumMap other) {
    for (Integer key : other.getVariableNums()) {
      if (varMap.containsKey(key)
          && (varMap.get(key) != other.varMap.get(key) || !names.get(key).equals(
              other.names.get(key)))) {
        throw new IllegalArgumentException(
            "Conflicting number -> (name, variable) mapping! This object: "
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
    BiMap<Integer, String> newNames = HashBiMap.create();
    for (Integer key : varNumsToKeep) {
      if (contains(key)) {
        newVarMap.put(key, varMap.get(key));
        newNames.put(key, names.get(key));
      }
    }
    return new VariableNumMap(newVarMap, newNames);
  }

  /**
   * Returns a {@code VariableNumMap} containing only the variable with number
   * {@code varNumToKeep}. If this map does not contain such a variable, returns
   * an empty map.
   * 
   * @param varNumsToKeep
   * @return
   */
  public VariableNumMap intersection(int... varNumsToKeep) {
    return intersection(Ints.asList(varNumsToKeep));
  }

  /**
   * Removes {@code variableNum} from {@code this}.
   * 
   * @param variableNum
   * @return
   */
  public VariableNumMap remove(int variableNum) {
    return removeAll(Ints.asList(variableNum));
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
    BiMap<Integer, String> newNames = HashBiMap.create(names);
    Set<Integer> varNumsToRemoveSet = Sets.newHashSet(varNumsToRemove);
    for (Integer key : getVariableNums()) {
      if (varNumsToRemoveSet.contains(key)) {
        newVarMap.remove(key);
        newNames.remove(key);
      }
    }
    return new VariableNumMap(newVarMap, newNames);
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
    BiMap<Integer, String> newNames = HashBiMap.create(names);
    for (Integer key : other.getVariableNums()) {
      newVarMap.put(key, other.varMap.get(key));
      newNames.put(key, other.names.get(key));
    }
    return new VariableNumMap(newVarMap, newNames);
  }

  /**
   * Adds or replaces a single number/variable mapping in this map.
   * 
   * @param num
   * @param var
   * @return
   */
  public VariableNumMap addMapping(int num, String name, Variable var) {
    SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>(
        varMap);
    newVarMap.put(num, var);
    BiMap<Integer, String> newNames = HashBiMap.create(names);
    newNames.put(num, name);
    return new VariableNumMap(newVarMap, newNames);
  }

  /**
   * Gets the {@code numVariables} in this with the lowest variable nums.
   * 
   * @param numVariables
   * @return
   */
  public VariableNumMap getFirstVariables(int numVariables) {
    SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>();
    BiMap<Integer, String> newNames = HashBiMap.create();

    for (Integer key : varMap.keySet()) {
      if (newVarMap.size() >= numVariables) {
        break;
      }

      newVarMap.put(key, varMap.get(key));
      newNames.put(key, names.get(key));
    }
    return new VariableNumMap(newVarMap, newNames);
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
      returnValue.add(assignment.getValue(varNum));
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
    Preconditions.checkArgument(outcome.size() == varMap.size(),
        "outcome %s cannot be assigned to %s (wrong number of values)", outcome, this);

    Map<Integer, Object> varValueMap = new HashMap<Integer, Object>();
    int i = 0;
    for (Map.Entry<Integer, Variable> varIndex : varMap.entrySet()) {
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
   * Same as {@link #outcomeToAssignment(Object[])}, but using a varargs
   * parameter.
   * 
   * @param outcome
   * @return
   */
  public Assignment outcomeArrayToAssignment(Object... outcome) {
    return outcomeToAssignment(outcome);
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
        value[index] = ((DiscreteVariable) entry.getValue()).getValueIndex(assignment
            .getValue(entry.getKey()));
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
   * values for the variables in {@code this}. {@code assignment} must contain a
   * subset of the variables in {@code this}.
   * 
   * @param assignment
   * @return
   */
  public boolean isValidAssignment(Assignment assignment) {
    Preconditions.checkArgument(containsAll(assignment.getVariableNums()));
    for (Integer varNum : assignment.getVariableNums()) {
      if (!varMap.get(varNum).canTakeValue(assignment.getValue(varNum))) {
        return false;
      }
    }
    return true;
  }
  
  /**
   * Returns {@code true} if {@code values} can be converted into an
   * assignment to these variables.
   * 
   * @param values
   * @return
   */
  public boolean isValidOutcomeArray(Object... outcomes) {
    return isValidAssignment(outcomeArrayToAssignment(outcomes));
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (Integer varNum : varMap.keySet()) {
      sb.append(names.get(varNum));
      sb.append("=");
      sb.append(varMap.get(varNum));
      sb.append(",");
    }
    sb.append("]");
    return sb.toString();
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
    if (o instanceof VariableNumMap) {
      VariableNumMap other = (VariableNumMap) o;
      return varMap.equals(other.varMap) && names.equals(other.names);
    }
    return false;
  }

  /**
   * Get a VariableNumMap with no num -> variable mappings.
   */
  public static VariableNumMap emptyMap() {
    List<Variable> empty = Collections.emptyList();
    return new VariableNumMap(Arrays.<Integer> asList(),
        Arrays.<String> asList(), empty);
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

  public static VariableNumMap unionAll(VariableNumMap... maps) {
    return unionAll(Arrays.asList(maps));
  }

  /**
   * Converter from assignments to outcomes (list of objects) and vice-versa.
   * 
   * The converter maps {@code null} inputs to {@code null} outputs.
   * 
   * @author jayantk
   */
  private static class AssignmentConverter extends Converter<List<Object>, Assignment> {

    private final VariableNumMap variables;

    public AssignmentConverter(VariableNumMap variables) {
      this.variables = variables;
    }

    @Override
    public Assignment apply(List<Object> item) {
      return (item == null) ? null : variables.outcomeToAssignment(item);
    }

    @Override
    public List<Object> invert(Assignment item) {
      return (item == null) ? null : variables.assignmentToOutcome(item);
    }
  }

  /**
   * A one-to-one conversion function between two sets of variable indices and
   * names. A {@code VariableRelabeling} can be applied to
   * {@code VariableNumMap}s from either set to get the corresponding map using
   * the other set of indices and names.
   * 
   * @author jayantk
   */
  public static class VariableRelabeling extends Converter<VariableNumMap, VariableNumMap> implements Serializable {

    private static final long serialVersionUID = -2598135542480496918L;

    private final BiMap<Integer, Integer> variableIndexMap;
    private final BiMap<String, String> variableNameMap;

    public VariableRelabeling(BiMap<Integer, Integer> variableIndexMap,
        BiMap<String, String> variableNameMap) {
      this.variableIndexMap = variableIndexMap;
      this.variableNameMap = variableNameMap;
    }

    public String getReplacementName(String name) {
      return variableNameMap.get(name);
    }

    public Integer getReplacementIndex(Integer index) {
      return variableIndexMap.get(index);
    }

    public boolean isInDomain(VariableNumMap variableNumMap) {
      return variableIndexMap.keySet().containsAll(variableNumMap.getVariableNums()) &&
          variableNameMap.keySet().containsAll(variableNumMap.getVariableNames());
    }

    public boolean isInRange(VariableNumMap variableNumMap) {
      return variableIndexMap.values().containsAll(variableNumMap.getVariableNums()) &&
          variableNameMap.values().containsAll(variableNumMap.getVariableNames());
    }

    public BiMap<Integer, Integer> getVariableIndexReplacementMap() {
      return variableIndexMap;
    }

    @Override
    public VariableNumMap apply(VariableNumMap input) {
      Preconditions.checkArgument(isInDomain(input));
      return mapIndicesAndNames(input, variableIndexMap, variableNameMap);
    }

    @Override
    public VariableNumMap invert(VariableNumMap input) {
      Preconditions.checkArgument(isInRange(input));
      return mapIndicesAndNames(input, variableIndexMap.inverse(), variableNameMap.inverse());
    }

    /*
     * Override the default implementation so that the non-Converter methods
     * work as expected on the inverse operation.
     */
    @Override
    public VariableRelabeling inverse() {
      return new VariableRelabeling(variableIndexMap.inverse(), variableNameMap.inverse());
    }

    @Override
    public String toString() {
      return variableIndexMap.toString();
    }

    /**
     * Expects {@code other} and {@code this} to contain mappings for disjoint
     * sets of variables.
     * 
     * @param other
     * @return
     */
    public VariableRelabeling union(VariableRelabeling other) {
      BiMap<Integer, Integer> newVariableIndexMap = HashBiMap.create(variableIndexMap);
      BiMap<String, String> newVariableNameMap = HashBiMap.create(variableNameMap);

      newVariableIndexMap.putAll(other.variableIndexMap);
      newVariableNameMap.putAll(other.variableNameMap);

      return new VariableRelabeling(newVariableIndexMap, newVariableNameMap);
    }

    /**
     * Constructs a relabeling from {@code domain} to {@code range} by mapping
     * the {@code i}th variable name/index in {@code domain} to the {@code i}th
     * name/index in {@code range}. Requires
     * {@code domain.size() == range.size()}.
     * 
     * @param domain
     * @param range
     */
    public static VariableRelabeling createFromVariables(VariableNumMap domain, VariableNumMap range) {
      Preconditions.checkArgument(domain.size() == range.size());
      BiMap<Integer, Integer> indexMap = HashBiMap.create();
      BiMap<String, String> nameMap = HashBiMap.create();
      for (int i = 0; i < domain.size(); i++) {
        indexMap.put(domain.getVariableNums().get(i), range.getVariableNums().get(i));
        nameMap.put(domain.getVariableNames().get(i), range.getVariableNames().get(i));
      }
      return new VariableRelabeling(indexMap, nameMap);
    }

    /**
     * Constructs the identity relabeling between variables in {@code map}.
     * 
     * @param map
     * @return
     */
    public static VariableRelabeling identity(VariableNumMap map) {
      BiMap<Integer, Integer> indexMap = HashBiMap.create();
      BiMap<String, String> nameMap = HashBiMap.create();
      for (int i = 0; i < map.size(); i++) {
        indexMap.put(map.getVariableNums().get(i), map.getVariableNums().get(i));
        nameMap.put(map.getVariableNames().get(i), map.getVariableNames().get(i));
      }
      return new VariableRelabeling(indexMap, nameMap);
    }

    /**
     * Replaces each index in {@code inputVar} with its corresponding value in
     * {@code indexReplacements}, and similarly replaces each name in
     * {@code inputVar} with its value in {@code nameReplacements}.
     * 
     * @param inputVar
     * @param indexReplacements
     * @param nameReplacements
     * @return
     */
    private static VariableNumMap mapIndicesAndNames(VariableNumMap input,
        Map<Integer, Integer> indexReplacements, Map<String, String> nameReplacements) {
      SortedMap<Integer, Variable> newVarMap = new TreeMap<Integer, Variable>();
      BiMap<Integer, String> newNames = HashBiMap.create();
      for (Map.Entry<Integer, Variable> entry : input.varMap.entrySet()) {
        newVarMap.put(indexReplacements.get(entry.getKey()), entry.getValue());
        String oldVariableName = input.getVariableNameFromIndex(entry.getKey());
        newNames.put(indexReplacements.get(entry.getKey()), nameReplacements.get(oldVariableName));
      }
      return new VariableNumMap(newVarMap, newNames);
    }
  }
}
