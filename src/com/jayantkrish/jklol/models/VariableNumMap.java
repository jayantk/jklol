package com.jayantkrish.jklol.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Converter;
import com.jayantkrish.jklol.util.IntBiMap;

/**
 * A {@code VariableNumMap} represents a set of variables in a
 * graphical model. Each variable has a unique numerical index, a
 * unique name, and a {@code Variable} representing the type of its
 * values. {@code VariableNumMap}s are immutable.
 * 
 * @author jayant
 */
public class VariableNumMap implements Serializable {

  private static final long serialVersionUID = 2L;

  private final int[] nums;
  private final String[] names;
  private final Variable[] vars;

  /**
   * A {@code VariableNumMap} containing no variables.
   */
  public static final VariableNumMap EMPTY = new VariableNumMap(
      new int[0], new String[0], new Variable[0]);

  /**
   * Instantiate a VariableNumMap with the specified variables. Each
   * variable is named by both a unique integer id and a (possibly not
   * unique) String name. All three passed in lists must be of the
   * same size.
   * 
   * @param varNums The unique integer id of each variable
   * @param varNames The String name of each variable
   * @param vars The Variable type of each variable
   */
  public VariableNumMap(List<Integer> varNums, List<String> varNames, List<? extends Variable> vars) {
    Preconditions.checkArgument(varNums.size() == vars.size());
    Preconditions.checkArgument(varNums.size() == varNames.size());
    
    this.nums = Ints.toArray(varNums);
    this.names = varNames.toArray(new String[varNames.size()]);
    this.vars = vars.toArray(new Variable[vars.size()]);
    ArrayUtils.sortKeyValuePairs(nums, new Object[][]{names, this.vars}, 0, nums.length);
  }

  /**
   * Constructor used internally for building the results of
   * operations that return new {@code VariableNumMap}s, such as
   * {@link #union(VariableNumMap)}.
   * 
   * @param nums
   * @param vars
   * @param names
   */
  private VariableNumMap(int[] nums, String[] names, Variable[] vars) {
    Preconditions.checkArgument(nums.length == names.length);
    Preconditions.checkArgument(nums.length == vars.length);
    this.nums = nums;
    this.names = names;
    this.vars = vars;
  }

  public static final VariableNumMap fromVariableNames(List<String> variableNames,
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
  public static final VariableNumMap singleton(int varNum, String varName, Variable variable) {
    return new VariableNumMap(new int[] {varNum}, new String[] {varName}, new Variable[] {variable});
  }
  
  public static final VariableNumMap fromUnsortedArrays(int[] nums, String[] names, Variable[] vars) {
    ArrayUtils.sortKeyValuePairs(nums, new Object[][] {names, vars}, 0, nums.length);
    return new VariableNumMap(nums, names, vars);
  }
  
  public static final VariableNumMap fromUnsortedArrays(int[] nums, String[] names, Variable[] vars, int num) {
    if (num < nums.length) {
      return fromUnsortedArrays(Arrays.copyOf(nums, num), Arrays.copyOf(names, num), Arrays.copyOf(vars, num));
    } else {
      return fromUnsortedArrays(nums, names, vars);
    }
  }

  public static final VariableNumMap fromSortedArrays(int[] nums, String[] names, Variable[] vars) {
    return new VariableNumMap(nums, names, vars);
  }

  public static final VariableNumMap fromSortedArrays(int[] nums, String[] names, Variable[] vars, int num) {
    if (num < nums.length) {
      return fromSortedArrays(Arrays.copyOf(nums, num), Arrays.copyOf(names, num), Arrays.copyOf(vars, num));
    } else {
      return fromSortedArrays(nums, names, vars);
    }
  }

  /**
   * Converts a list of outcomes into a list of assignments. Each row
   * of {@code outcomes} is converted into an assignment by mapping
   * the ith column of {@code outcomes[i]} to an assignment of the ith
   * variable in {@code variables}. {@code null} entries are ignored.
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
  public final int size() {
    return nums.length;
  }

  /**
   * Get the numbers of the variables in this map, in ascending sorted
   * order.
   * 
   * @return
   */
  public final List<Integer> getVariableNums() {
    return Ints.asList(nums);
  }

  /**
   * Get the numbers of the variables in this map as an array, in
   * ascending sorted order.
   * 
   * @return
   */
  public final int[] getVariableNumsArray() {
    return nums;
  }
  
  /**
   * Get the variable types in this map, ordered by variable index.
   * 
   * @return
   */
  public final List<Variable> getVariables() {
    return Arrays.asList(vars);
  }
  
  public final Variable[] getVariablesArray() {
    return vars;
  }

  /**
   * Gets the names of all of the variables in {@code this}, ordered
   * by their variable index.
   * 
   * @return
   */
  public final List<String> getVariableNames() {
    return Arrays.asList(names);
  }

  public final String[] getVariableNamesArray() {
    return names;
  }

  /**
   * Gets the number of the sole variable contained in {@code this}.
   * Requires {@code this.size() == 1}.
   * 
   * @return
   */
  public final int getOnlyVariableNum() {
    Preconditions.checkState(nums.length == 1);
    return nums[0];
  }

  public final String getOnlyVariableName() {
    Preconditions.checkState(names.length == 1);
    return names[0];
  }
  
  /**
   * Gets the {@code Variable} of the sole variable contained in
   * {@code this}. Requires {@code this.size() == 1}.
   * 
   * @return
   */
  public final Variable getOnlyVariable() {
    Preconditions.checkState(vars.length == 1);
    return vars[0];
  }

  /**
   * Gets the name of the variable whose numerical index is
   * {@code index}. Returns {@code null} if no such variable
   * exists.
   * 
   * @return
   */
  public final String getVariableNameFromIndex(int num) {
    int index = getVariableIndex(num);
    if (index >= 0) {
      return names[index];
    } else {
      return null;
    }
  }

  /**
   * Get the discrete variables in this map, ordered by variable
   * index.
   */
  public final List<DiscreteVariable> getDiscreteVariables() {
    List<DiscreteVariable> discreteVars = new ArrayList<DiscreteVariable>();
    for (int i = 0; i < vars.length; i++) {
      if (vars[i] instanceof DiscreteVariable) {
        discreteVars.add((DiscreteVariable) vars[i]);
      }
    }
    return discreteVars;
  }

  /**
   * Gets an array containing the number of possible values for each
   * variable in this. Requires all {@code Variable}s in this to have
   * type {@code DiscreteVariable}. The returned size array is sorted
   * by dimension number.
   * 
   * @return
   */
  public final int[] getVariableSizes() {
    int[] sizes = new int[size()];
    for (int i = 0; i < vars.length; i++) {
      sizes[i] = ((DiscreteVariable) vars[i]).numValues();
    }
    return sizes;
  }

  /**
   * Gets an array containing the number of possible joint assignments
   * to the variables in {@code this}. Requires all {@code Variable}s
   * in this to be {@code DiscreteVariable}s.
   * 
   * @return
   */
  public final int getNumberOfPossibleAssignments() {
    int[] sizes = getVariableSizes();
    int numAssignments = 1;
    for (int i = 0; i < sizes.length; i++) {
      numAssignments *= sizes[i];
    }
    return numAssignments;
  }

  /**
   * Gets any variables in {@code this} whose values are objects of
   * type {@code T}.
   * 
   * @return
   */
  public final List<ObjectVariable> getObjectVariables() {
    List<ObjectVariable> objectVars = new ArrayList<ObjectVariable>();
    for (int i = 0; i < vars.length; i++) {
      if (vars[i] instanceof ObjectVariable) {
        objectVars.add((ObjectVariable) vars[i]);
      }
    }
    return objectVars;
  }

  /**
   * Gets any boolean-valued variables in {@code this}.
   * 
   * @return
   */
  public final List<BooleanVariable> getBooleanVariables() {
    List<BooleanVariable> booleanVars = new ArrayList<BooleanVariable>();
    for (int i = 0; i < vars.length; i++) {
      if (vars[i] instanceof BooleanVariable) {
        booleanVars.add((BooleanVariable) vars[i]);
      }
    }
    return booleanVars;
  }
  
  private final int getVariableIndex(int varNum) {
    return Arrays.binarySearch(nums, varNum);
  }
  
  private final int getVariableIndex(String name) {
    for (int i = 0; i < names.length; i++) {
      if (names[i].equals(name)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Get the variable referenced by a particular variable number.
   * Returns {@code null} if no variable exists with that number.
   * 
   * @param variableNum
   * @return
   */
  public final Variable getVariable(int variableNum) {
    int index = getVariableIndex(variableNum);
    if (index >= 0) {
      return vars[index];
    } else {
      return null;
    }
  }

  /**
   * Gets the index of the variable named {@code variableName}. Returns
   * {@code -1} if no such variable exists.
   * 
   * @param variableName
   * @return
   */
  public final int getVariableByName(String variableName) {
    int index = getVariableIndex(variableName);
    if (index == -1) {
      return index;
    } else {
      return nums[index];
    }
  }

  /**
   * Gets the subset of {@code this} containing variables with a name
   * in {@code variableNames}. Names in {@code variableNames} which
   * are not in {@code this} are ignored.
   * 
   * @param variableNames
   * @return
   */
  public final VariableNumMap getVariablesByName(Collection<String> variableNames) {
    int[] newNums = new int[nums.length];
    String[] newNames = new String[nums.length];
    Variable[] newVars = new Variable[nums.length];

    int numFilled = 0;
    for (int i = 0; i < nums.length; i++) {
      if (variableNames.contains(names[i])) {
        newNums[numFilled] = nums[i];
        newNames[numFilled] = names[i];
        newVars[numFilled] = vars[i];
        numFilled++;
      }
    }
    return VariableNumMap.fromSortedArrays(newNums, newNames, newVars, numFilled);
  }

  /**
   * Identical to {@link #getVariablesByName(Collection)}, but with an
   * array of names instead of a {@code Collection}.
   * 
   * @param variableNames
   * @return
   */
  public final VariableNumMap getVariablesByName(String... variableNames) {
    return getVariablesByName(Arrays.asList(variableNames));
  }

  /**
   * Gets all variables whose names begin with {@code namePrefix}.
   * 
   * @param namePrefix
   * @return
   */
  public final VariableNumMap getVariablesByNamePrefix(String namePrefix) {
    int[] newNums = new int[nums.length];
    String[] newNames = new String[nums.length];
    Variable[] newVars = new Variable[nums.length];

    int numFilled = 0;
    for (int i = 0; i < nums.length; i++) {
      if (names[i].startsWith(namePrefix)) {
        newNums[numFilled] = nums[i];
        newNames[numFilled] = names[i];
        newVars[numFilled] = vars[i];
        numFilled++;
      }
    }
    return VariableNumMap.fromSortedArrays(newNums, newNames, newVars, numFilled);
  }

  /**
   * Returns true if variableNum is mapped to a variable in this map.
   * 
   * @param variableNum
   * @return
   */
  public final boolean contains(int variableNum) {
    return getVariableIndex(variableNum) >= 0;
  }

  /**
   * Returns {@code true} if {@code this} contains a variable named
   * {@code variableName}.
   * 
   * @param variableName
   * @return
   */
  public final boolean contains(String variableName) {
    return getVariableIndex(variableName) >= 0;
  }
  
  public final boolean containsAll(int... varNums) {
    for (int i : varNums) {
      if (!contains(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns {@code true} if every variable number in
   * {@code variableNums} is mapped to a variable in {@code this} map.
   * Returns {@code true} if {@code variableNums} is empty.
   * 
   * @param variableNums
   * @return
   */
  public final boolean containsAll(Collection<Integer> variableNums) {
    return containsAll(Ints.toArray(variableNums));
  }

  /**
   * Same as {@link #containsAll(Collection)}, using the variable
   * numbers in the passed map.
   * 
   * @param other
   * @return
   */
  public final boolean containsAll(VariableNumMap other) {
    return containsAll(other.getVariableNumsArray());
  }
  
  /**
   * Returns {@code true} if {@code other} contains a subset of the
   * variable numbers in {@code this}, and those variables have the
   * same names.
   * 
   * @param other
   * @return
   */
  public final boolean containsAllNames(VariableNumMap other) {
    int[] otherNums = other.nums;
    String[] otherNames = other.names;

    for (int i = 0; i < otherNums.length; i++) {
      int index = getVariableIndex(otherNums[i]);
      if (index < 0 || !otherNames[i].equals(names[index])) {
        return false;
      }
    }
    return true;
  }

  public final boolean containsAny(int... varNums) {
    for (int i : varNums) {
      if (contains(i)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} if any variable number in
   * {@code variableNums} is mapped to a variable in {@code this} map.
   * Returns {@code false} if {@code variableNums} is empty.
   * 
   * @param variableNums
   * @return
   */
  public final boolean containsAny(Collection<Integer> variableNums) {
    return containsAny(Ints.toArray(variableNums));
  }

  /**
   * Same as {@link #containsAny(Collection)}, using the variable
   * numbers in the passed map.
   * 
   * @param other
   * @return
   */
  public final boolean containsAny(VariableNumMap other) {
    return containsAny(other.getVariableNumsArray());
  }

  /**
   * Ensures that all variable numbers which are shared between other
   * and this are mapped to the same variables.
   */
  private final void checkCompatibility(VariableNumMap other) {
    int i = 0, j = 0;
    int[] otherNums = other.nums;
    String[] otherNames = other.names;
    Variable[] otherVars = other.vars;
    while (i < nums.length && j < otherNums.length) {
      if (nums[i] < otherNums[j]) {
        i++;
      } else if (nums[i] > otherNums[j]) {
        j++;
      } else {
        // Equal
        Preconditions.checkArgument(names[i].equals(otherNames[j]));
        Preconditions.checkArgument(vars[i].getName().equals(otherVars[j].getName()));
        i++; j++;
      }
    }
  }

  /**
   * Returns a {@code VariableNumMap} containing only the variables
   * with numbers in {@code varNumToKeep}.
   * 
   * @param varNumsToKeep
   * @return
   */
  public final VariableNumMap intersection(int... varNumsToKeep) {
    if (varNumsToKeep.length == 0) {
      return VariableNumMap.EMPTY;
    }

    int[] newNums = new int[varNumsToKeep.length];
    String[] newNames = new String[varNumsToKeep.length];
    Variable[] newVars = new Variable[varNumsToKeep.length];
    
    int numFilled = 0;
    for (int varNum : varNumsToKeep) {
      int index = getVariableIndex(varNum);
      if (index >= 0) {
        newNums[numFilled] = nums[index];
        newNames[numFilled] = names[index];
        newVars[numFilled] = vars[index];
        numFilled++;
      }
    }
    return VariableNumMap.fromUnsortedArrays(newNums, newNames, newVars, numFilled);
  }

  /**
   * Return a VariableNumMap containing all variable numbers shared by
   * both maps.
   * 
   * @param other
   * @return
   */
  public final VariableNumMap intersection(VariableNumMap other) {
    checkCompatibility(other);
    return intersection(other.getVariableNumsArray());
  }

  /**
   * Return a VariableNumMap containing all variable numbers shared by
   * varNumsToKeep and this.getVariableNums()
   * 
   * @param varNumsToKeep
   * @return
   */
  public final VariableNumMap intersection(Collection<Integer> varNumsToKeep) {
    return intersection(Ints.toArray(varNumsToKeep));
  }

  /**
   * Returns a copy of this map with every variable in 
   * {@code variableNums} removed.
   * 
   * @param variableNums
   * @return
   */
  public final VariableNumMap removeAll(int ... variableNums) {
    if (variableNums.length == 0) {
      return this;
    }

    int[] newNums = new int[nums.length];
    String[] newNames = new String[nums.length];
    Variable[] newVars = new Variable[nums.length];
    int numFilled = 0;
    for (int i = 0; i < newNums.length; i++) {
      if (!Ints.contains(variableNums, nums[i])) {
        newNums[numFilled] = nums[i];
        newNames[numFilled] = names[i];
        newVars[numFilled] = vars[i];
        numFilled++;
      }
    }
    return VariableNumMap.fromSortedArrays(newNums, newNames, newVars, numFilled);
  }

  /**
   * Removes all variable mappings whose numbers are in other.
   * 
   * @param varNumsToRemove
   * @return
   */
  public final VariableNumMap removeAll(VariableNumMap other) {
    checkCompatibility(other);
    return removeAll(other.getVariableNumsArray());
  }

  /**
   * Removes all variable mappings whose numbers are in
   * varNumsToRemove.
   * 
   * @param varNumsToRemove
   * @return
   */
  public final VariableNumMap removeAll(Collection<Integer> varNumsToRemove) {
    return removeAll(Ints.toArray(varNumsToRemove));
  }

  /**
   * Returns a {@code VariableNumMap} containing the union of the
   * number->variable mappings from this map and other. The maps may
   * not contain conflicting mappings for any number.
   * 
   * @param other
   * @return
   */
  public final VariableNumMap union(VariableNumMap other) {
    if (other.size() == 0) {
      return this;
    }

    int[] otherNums = other.nums;
    String[] otherNames = other.names;
    Variable[] otherVars = other.vars;
    int[] newNums = new int[nums.length + otherNums.length];
    String[] newNames = new String[nums.length + otherNums.length];
    Variable[] newVars = new Variable[nums.length + otherNums.length];
    int i = 0, j = 0, numFilled = 0;
    while (i < nums.length && j < otherNums.length) {
      if (nums[i] < otherNums[j]) {
        newNums[numFilled] = nums[i];
        newNames[numFilled] = names[i];
        newVars[numFilled] = vars[i];
        i++; numFilled++;
      } else if (nums[i] > otherNums[j]) {
        newNums[numFilled] = otherNums[j];
        newNames[numFilled] = otherNames[j];
        newVars[numFilled] = otherVars[j];
        j++; numFilled++;
      } else {
        // Equal. Both maps must have the same values for this variable
        Preconditions.checkArgument(names[i].equals(otherNames[j]));
        Preconditions.checkArgument(vars[i].getName().equals(otherVars[j].getName()));            checkCompatibility(other);

        newNums[numFilled] = nums[i];
        newNames[numFilled] = names[i];
        newVars[numFilled] = vars[i];
        i++; j++; numFilled++;
      }
    }

    // Finish off any remaining entries.
    while (i < nums.length) {
      newNums[numFilled] = nums[i];
      newNames[numFilled] = names[i];
      newVars[numFilled] = vars[i];
      i++; numFilled++;
    }

    while (j < otherNums.length) {
      newNums[numFilled] = otherNums[j];
      newNames[numFilled] = otherNames[j];
      newVars[numFilled] = otherVars[j];
      j++; numFilled++;
    }

    return VariableNumMap.fromSortedArrays(newNums, newNames, newVars, numFilled);
  }

  /**
   * Adds a single number/variable mapping to this map.
   * 
   * @param num
   * @param var
   * @return
   */
  public final VariableNumMap addMapping(int num, String name, Variable var) {
    return union(VariableNumMap.singleton(num, name, var));
  }

  public final VariableNumMap relabelVariableNums(int[] relabeling) {
    Preconditions.checkArgument(nums.length == relabeling.length);

    int[] newNums = new int[nums.length];
    String[] newNames = new String[nums.length];
    Variable[] newVars = new Variable[nums.length];    
    for (int i = 0; i < nums.length; i++) {
      newNums[i] = relabeling[i];
      newNames[i] = names[i];
      newVars[i] = vars[i];
    }
    return VariableNumMap.fromUnsortedArrays(newNums, newNames, newVars);
  }

  /**
   * Gets the {@code numVariables} in this with the lowest variable
   * nums.
   * 
   * @param numVariables
   * @return
   */
  public VariableNumMap getFirstVariables(int numVariables) {
    int numToCopy = Math.min(numVariables, nums.length);
    int[] newNums = Arrays.copyOf(nums, numToCopy);
    String[] newNames = Arrays.copyOf(names, numToCopy);
    Variable[] newVars = Arrays.copyOf(vars, numToCopy);
    return VariableNumMap.fromSortedArrays(newNums, newNames, newVars);
  }

  /**
   * Gets the values of the variables in {@code Assignment} and
   * returns them as a {@code List}. This operation is the inverse of
   * {@link #outcomeToAssignment(List)}. The size of the returned list
   * is equal to {@code this.size()}.
   * 
   * @param assignment
   * @return
   */
  public List<Object> assignmentToOutcome(Assignment assignment) {
    List<Object> returnValue = Lists.newArrayList();
    for (int varNum : nums) {
      returnValue.add(assignment.getValue(varNum));
    }
    return returnValue;
  }

  /**
   * Get the assignment corresponding to a particular setting of the
   * variables in this set. The Objects in outcome are assumed to be
   * ordered in ascending order by variable number. (i.e., the ith
   * object is the value of the ith variable returned by
   * getVariableNums())
   */
  public Assignment outcomeToAssignment(List<? extends Object> outcome) {
    return outcomeToAssignment(outcome.toArray());
  }

  /**
   * Get the assignment corresponding to a particular setting of the
   * variables in this factor.
   */
  public Assignment outcomeToAssignment(Object[] outcome) {
    Preconditions.checkArgument(outcome.length == nums.length,
        "outcome %s cannot be assigned to %s (wrong number of values)", outcome, this);
    return Assignment.fromSortedArrays(nums, outcome);
  }

  /**
   * Same as {@link #outcomeToAssignment(Object[])}, but using a
   * varargs parameter.
   * 
   * @param outcome
   * @return
   */
  public Assignment outcomeArrayToAssignment(Object... outcome) {
    return outcomeToAssignment(outcome);
  }

  /**
   * Gets a converter for transforming outcomes (settings of variables
   * in {@code this}) into their corresponding assignments, and vice
   * versa. The returned converter performs the functions of
   * {@link #outcomeToAssignment(List)} and
   * {@link #assignmentToOutcome(Assignment)}.
   * 
   * @return
   */
  public Converter<List<Object>, Assignment> getOutcomeToAssignmentConverter() {
    return new AssignmentConverter(this);
  }

  /**
   * Converts an assignment over a set of {@code DiscreteVariable}s
   * into an equivalent {@code int[]} representation. This method is
   * used to efficiently store the possible assignments to discrete
   * variables. The returned array has length equal to
   * {@code this.size()}, and can be converted back into an assignment
   * using {@link #intArrayToAssignment(int[])}.
   * <p>
   * If {@code assignment} contains values which are not in the domain
   * of the corresponding discrete variables, this method throws an
   * exception.
   * 
   * @param assignment
   * @return
   */
  public int[] assignmentToIntArray(Assignment assignment) {
    int[] value = new int[nums.length];
    for (int i = 0; i < nums.length; i++) {
      Preconditions.checkState(vars[i] instanceof DiscreteVariable);
      Preconditions.checkArgument(assignment.contains(nums[i]),
          "Partial assignment provided to assignmentToIntArray. Assignment: %s, variables: %s", assignment, this);
      value[i] = ((DiscreteVariable) vars[i]).getValueIndex(assignment.getValue(nums[i]));
    }
    return value;
  }

  /**
   * Converts the passed {@code int[]} of variable value indices into
   * an {@code Assignment} by mapping it through the
   * {@code DiscreteVariable}s contained in {@code this}. This
   * operation is the inverse of
   * {@link #assignmentToIntArray(Assignment)}
   * 
   * @param values
   * @return
   */
  public Assignment intArrayToAssignment(int[] values) {
    Object[] objectValues = new Object[nums.length];
    for (int i = 0; i < nums.length; i++) {
      objectValues[i] = ((DiscreteVariable) vars[i]).getValue(values[i]);
    }
    return Assignment.fromSortedArrays(nums, objectValues);
  }

  /**
   * Returns {@code true} if the values in {@code assignment} are
   * possible values for the variables in {@code this}.
   * {@code assignment} must contain a subset of the variables in
   * {@code this}.
   * 
   * @param assignment
   * @return
   */
  public boolean isValidAssignment(Assignment assignment) {
    Preconditions.checkArgument(containsAll(assignment.getVariableNumsArray()));
    for (int varNum : assignment.getVariableNumsArray()) {
      int index = getVariableIndex(varNum);
      if (!vars[index].canTakeValue(assignment.getValue(varNum))) {
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
    for (int i = 0; i < nums.length; i++) {
      sb.append(i);
      sb.append(":");
      sb.append(names[i]);
      sb.append("=");
      sb.append(vars[i]);
      sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(nums) * 123 + Arrays.hashCode(names) * 31 
        + Arrays.hashCode(vars);
  }

  /**
   * {@code VariableNumMap}s are equal if they contain exactly the
   * same variable number -> variable mappings.
   */
  @Override
  public boolean equals(Object o) {
    if (o instanceof VariableNumMap) {
      VariableNumMap other = (VariableNumMap) o;
      return Arrays.equals(nums, other.nums) && Arrays.deepEquals(names, other.names) 
          && Arrays.deepEquals(vars, other.vars);
    }
    return false;
  }

  /**
   * Returns the union of all of the passed-in maps, which may not
   * contain conflicting mappings for any variable number.
   * 
   * @param varNumMaps
   * @return
   */
  public static VariableNumMap unionAll(Collection<VariableNumMap> varNumMaps) {
    VariableNumMap curMap = EMPTY;
    for (VariableNumMap varNumMap : varNumMaps) {
      curMap = curMap.union(varNumMap);
    }
    return curMap;
  }

  public static VariableNumMap unionAll(VariableNumMap... maps) {
    return unionAll(Arrays.asList(maps));
  }

  /**
   * Converter from assignments to outcomes (list of objects) and
   * vice-versa.
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
   * A one-to-one conversion function between two sets of variable
   * indices and names. A {@code VariableRelabeling} can be applied to
   * {@code VariableNumMap}s from either set to get the corresponding
   * map using the other set of indices and names.
   * 
   * @author jayantk
   */
  public static class VariableRelabeling extends Converter<VariableNumMap, VariableNumMap> implements Serializable {

    private static final long serialVersionUID = 2L;

    private final VariableNumMap inputVars;
    private final VariableNumMap outputVars;

    // Mapping between the two sets of variables based on their numbers.
    private final IntBiMap varNumMap;
    
    public static final VariableRelabeling EMPTY = new VariableRelabeling(VariableNumMap.EMPTY, VariableNumMap.EMPTY,
        IntBiMap.fromSortedKeyValues(new int[0], new int[0]));

    public VariableRelabeling(VariableNumMap inputVars, VariableNumMap outputVars,
        IntBiMap varNumMap) {
      this.inputVars = Preconditions.checkNotNull(inputVars);
      this.outputVars = Preconditions.checkNotNull(outputVars);

      this.varNumMap = Preconditions.checkNotNull(varNumMap);
    }

    public String getReplacementName(String name) {
      int inputVarNum = inputVars.getVariableByName(name);
      if (inputVarNum != -1) {
        int outputVarNum = varNumMap.get(inputVarNum, -1);
        return outputVars.getVariableNameFromIndex(outputVarNum);
      }
      return null;
    }

    public Integer getReplacementIndex(Integer inputVarNum) {
      return varNumMap.get(inputVarNum, -1);
    }

    public boolean isInDomain(VariableNumMap variableNumMap) {
      return inputVars.containsAllNames(variableNumMap);
    }

    public boolean isInRange(VariableNumMap variableNumMap) {
      return outputVars.containsAllNames(variableNumMap);
    }

    public IntBiMap getVariableIndexReplacementMap() {
      return varNumMap;
    }

    @Override
    public VariableNumMap apply(VariableNumMap input) {
      return mapIndicesAndNames(input, inputVars, outputVars, varNumMap);
    }

    @Override
    public VariableNumMap invert(VariableNumMap input) {
      return mapIndicesAndNames(input, outputVars, inputVars, varNumMap.inverse());
    }

    /*
     * Override the default implementation so that the non-Converter
     * methods work as expected on the inverse operation.
     */
    @Override
    public VariableRelabeling inverse() {
      return new VariableRelabeling(outputVars, inputVars, varNumMap.inverse());
    }

    @Override
    public String toString() {
      return varNumMap.toString();
    }

    /**
     * Expects {@code other} and {@code this} to contain mappings for
     * disjoint sets of variables.
     * 
     * @param other
     * @return
     */
    public VariableRelabeling union(VariableRelabeling other) {
      VariableNumMap newInput = inputVars.union(other.inputVars);
      VariableNumMap newOutput = outputVars.union(other.outputVars);

      IntBiMap newRelabeling = varNumMap.union(other.varNumMap);

      return new VariableRelabeling(newInput, newOutput, newRelabeling);
    }

    /**
     * Constructs a relabeling from {@code domain} to {@code range} by
     * mapping the {@code i}th variable name/index in {@code domain}
     * to the {@code i}th name/index in {@code range}. Requires
     * {@code domain.size() == range.size()}.
     * 
     * @param domain
     * @param range
     */
    public static VariableRelabeling createFromVariables(VariableNumMap domain, VariableNumMap range) {
      Preconditions.checkArgument(domain.size() == range.size());
      IntBiMap map = IntBiMap.fromUnsortedKeyValues(domain.getVariableNumsArray(),
          range.getVariableNumsArray());
      return new VariableRelabeling(domain, range, map);
    }

    /**
     * Constructs the identity relabeling between variables in
     * {@code map}.
     * 
     * @param map
     * @return
     */
    public static VariableRelabeling identity(VariableNumMap map) {
      IntBiMap varNumMap = IntBiMap.fromUnsortedKeyValues(map.getVariableNumsArray(),
          map.getVariableNumsArray());
      return new VariableRelabeling(map, map, varNumMap);
    }

    private static VariableNumMap mapIndicesAndNames(VariableNumMap input,
        VariableNumMap domainVars, VariableNumMap rangeVars, IntBiMap mapping) {
      int[] newNums = new int[input.size()];
      String[] newNames = new String[input.size()];
      Variable[] newVars = new Variable[input.size()];

      for (int i = 0; i < input.size(); i++) {
        newNums[i] = mapping.get(input.nums[i], -1);
        Preconditions.checkArgument(newNums[i] != -1);
        newNames[i] = rangeVars.getVariableNameFromIndex(newNums[i]);
        newVars[i] = input.vars[i];
      }

      return VariableNumMap.fromUnsortedArrays(newNums, newNames, newVars);
    }
  }
}
