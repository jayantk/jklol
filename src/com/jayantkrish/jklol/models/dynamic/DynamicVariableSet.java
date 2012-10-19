package com.jayantkrish.jklol.models.dynamic;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * The variables for a dynamic factor graph, where the number of variables in
 * the graph is a function of the input instance. {@code DynamicVariableSet}s
 * convert to {@link VariableNumMap}s by conditioning on a
 * {@link DynamicAssignment}. During instantiation, a {@code DynamicVariableSet}
 * creates a {@code VariableNumMap} by repeating certain sets of template
 * variables ("plates"). The number of repetitions of each plate is dictated by
 * the input {@code DynamicAssignment}.
 * 
 * @author jayant
 */
public class DynamicVariableSet implements Serializable {

  private static final long serialVersionUID = 1L;

  private final VariableNumMap fixedVariables;
  private final int fixedVariableMaxInd;
  private final List<String> plateNames;
  private final List<DynamicVariableSet> plates;
  private final int[] maximumReplications;

  private static final String NAMESPACE_SEPARATOR = "/";

  public static final DynamicVariableSet EMPTY = new DynamicVariableSet(VariableNumMap.emptyMap(),
      Collections.<String> emptyList(), Collections.<DynamicVariableSet> emptyList(), new int[0]);

  public DynamicVariableSet(VariableNumMap fixedVariables,
      List<String> plateNames, List<DynamicVariableSet> plates, int[] maximumReplications) {
    Preconditions.checkArgument(plateNames.size() == plates.size());
    Preconditions.checkArgument(plateNames.size() == maximumReplications.length);
    this.fixedVariables = fixedVariables;
    this.fixedVariableMaxInd = (fixedVariables.size() > 0) ? Collections.max(fixedVariables.getVariableNums()) : 0;
    this.plateNames = plateNames;
    this.plates = plates;
    this.maximumReplications = maximumReplications;
  }

  /**
   * Creates a {@code DynamicVariableSet} containing {@code variables} and no
   * plates.
   * 
   * @param variables
   * @return
   */
  public static DynamicVariableSet fromVariables(VariableNumMap variables) {
    return new DynamicVariableSet(variables, Collections.<String> emptyList(),
        Collections.<DynamicVariableSet> emptyList(), new int[0]);
  }

  /**
   * Converts {@code values} into an assignment to the fixed (non-plate)
   * variables of {@code this}.
   * 
   * @param values
   * @return
   */
  public DynamicAssignment fixedVariableOutcomeToAssignment(List<? extends Object> values) {
    return DynamicAssignment.fromAssignment(fixedVariables.outcomeToAssignment(values));
  }

  public DynamicAssignment outcomeToAssignment(Object... values) {
    Preconditions.checkArgument(values.length == fixedVariables.size() + plateNames.size());
    return DynamicAssignment.fromAssignment(fixedVariables.outcomeToAssignment(values));
  }

  public DynamicAssignment plateOutcomeToAssignment(List<DynamicAssignment>... plateValues) {
    Preconditions.checkArgument(plateValues.length == plateNames.size());
    List<List<DynamicAssignment>> plateAssignments = Arrays.asList(plateValues);
    return new DynamicAssignment(Assignment.EMPTY, plateNames, plateAssignments);
  }

  public int getMaximumPlateSize() {
    int size = fixedVariableMaxInd + 1;
    for (int i = 0; i < plates.size(); i++) {
      size += plates.get(i).getMaximumPlateSize() * maximumReplications[i];
    }

    return size;
  }

  public VariableNumMap getFixedVariables() {
    return fixedVariables;
  }

  /**
   * Gets the variables which are replicated in the plate named
   * {@code plateName}.
   * 
   * @param plateName
   * @return
   */
  public DynamicVariableSet getPlate(String plateName) {
    int index = plateNames.indexOf(plateName);
    Preconditions.checkArgument(index != -1);
    return plates.get(index);
  }

  /**
   * Returns {@code true} if {@code assignment} is a legitimate assignment to
   * {@code this}.
   * 
   * @param assignment
   * @return
   */
  public boolean isValidAssignment(DynamicAssignment assignment) {
    if (!fixedVariables.isValidAssignment(assignment.getFixedAssignment())) {
      return false;
    }

    for (int i = 0; i < plateNames.size(); i++) {
      List<DynamicAssignment> plateValues = assignment.getPlateValue(plateNames.get(i));
      for (DynamicAssignment plateValue : plateValues) {
        if (!plates.get(i).isValidAssignment(plateValue)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Gets a {@code VariableNumMap} by replicating the plates in {@code this}.
   * {@code assignment} must contain a value (list of assignments) for all
   * plates in {@code this}; these values determine how many times each plate is
   * replicated in the returned {@code VariableNumMap}. If two assignments have
   * the same number of replications of each plate, this method will return the
   * same {@code VariableNumMap}.
   * 
   * @param assignment
   * @return
   */
  public VariableNumMap instantiateVariables(DynamicAssignment assignment) {
    List<String> varNames = Lists.newArrayList();
    List<Variable> variables = Lists.newArrayList();
    List<Integer> variableInds = Lists.newArrayList();

    instantiateVariablesHelper(assignment, varNames, variables, variableInds, "", 0);
    Preconditions.checkState(varNames.size() == variables.size());
    return new VariableNumMap(variableInds, varNames, variables);
  }

  private void instantiateVariablesHelper(DynamicAssignment assignment,
      List<String> variableNames, List<Variable> variables,
      List<Integer> variableInds, String namespace, int indexOffset) {
    // Add each fixedVariable to the output VariableNumMap.
    for (String varName : fixedVariables.getVariableNames()) {
      variableNames.add(namespace + varName);
      variables.add(fixedVariables.getVariable(fixedVariables.getVariableByName(varName)));
      variableInds.add(fixedVariables.getVariableByName(varName) + indexOffset);
    }

    for (int i = 0; i < plateNames.size(); i++) {
      Preconditions.checkArgument(assignment.containsPlateValue(plateNames.get(i)),
          "Cannot assign %s to %s", assignment, this); 
      List<DynamicAssignment> plateValues = assignment.getPlateValue(plateNames.get(i));
      // Allocate a fraction of the indices between startOffset and endOffset to
      // each plate.
      int plateIndex = indexOffset + getPlateStartIndex(i);
      for (int j = 0; j < plateValues.size(); j++) {
        plates.get(i).instantiateVariablesHelper(plateValues.get(j),
            variableNames, variables, variableInds,
            appendPlateToNamespace(namespace, plateNames.get(i), j),
            plateIndex);
        plateIndex += plates.get(i).getMaximumPlateSize();
      }

      // Ensure the plate doesn't overrun its index budget.
      Preconditions.checkState(plateIndex < indexOffset + getPlateEndIndex(i));
    }
  }

  /**
   * Gets the first variable index which can contain a replicated variable for
   * the {@code plateNum}th plate. The returned index is inclusive and may be
   * used by the plate.
   * 
   * @param plateNum
   * @return
   */
  private int getPlateStartIndex(int plateNum) {
    Preconditions.checkArgument(plateNum >= 0 && plateNum < plateNames.size());
    int startOffset = fixedVariableMaxInd + 1;
    for (int i = 0; i < plateNum; i++) {
      startOffset += plates.get(i).getMaximumPlateSize() * maximumReplications[i];
    }
    return startOffset;
  }

  /**
   * Gets the end of the block of variable indices which can contain a
   * replicated variable from the {@code plateNum}th plate. The returned index
   * is exclusive, i.e., all indices used by {@code plateNum} must be less than
   * the returned value.
   * 
   * @param plateName
   * @return
   */
  private int getPlateEndIndex(int plateNum) {
    Preconditions.checkArgument(plateNum >= 0 && plateNum < plateNames.size());
    return getPlateStartIndex(plateNum)
        + (plates.get(plateNum).getMaximumPlateSize() * maximumReplications[plateNum]);
  }

  public Assignment toAssignment(DynamicAssignment assignment) {
    Map<Integer, Object> values = Maps.newHashMap();
    toAssignmentHelper(assignment, 0, values);
    return new Assignment(values);
  }

  private void toAssignmentHelper(DynamicAssignment assignment, int indexOffset,
      Map<Integer, Object> values) {
    Assignment fixedAssignment = assignment.getFixedAssignment();
    Preconditions.checkArgument(fixedVariables.containsAll(fixedAssignment.getVariableNums()),
        "Cannot assign %s to %s", assignment, this);

    for (int curVarNum : fixedVariables.getVariableNums()) {
      if (fixedAssignment.contains(curVarNum)) {
        values.put(indexOffset + curVarNum, fixedAssignment.getValue(curVarNum));
      }
    }

    for (int i = 0; i < plateNames.size(); i++) {
      Preconditions.checkArgument(assignment.containsPlateValue(plateNames.get(i)),
          "Cannot assign %s to %s", assignment, this);
      int curOffset = indexOffset + getPlateStartIndex(i);
      for (DynamicAssignment plateValue : assignment.getPlateValue(plateNames.get(i))) {
        plates.get(i).toAssignmentHelper(plateValue, curOffset, values);
        curOffset += plates.get(i).getMaximumPlateSize();
      }
      Preconditions.checkState(curOffset < indexOffset + getPlateEndIndex(i));
    }
  }

  public DynamicAssignment toDynamicAssignment(Assignment assignment, VariableNumMap variables) {
    return toDynamicAssignmentHelper(assignment, variables, "");
  }

  private DynamicAssignment toDynamicAssignmentHelper(Assignment assignment,
      VariableNumMap variables, String namespace) {
    Map<Integer, Object> fixedAssignmentValues = Maps.newHashMap();
    for (int i = 0; i < fixedVariables.size(); i++) {
      // Get the variable's index in assignment.
      String curVarName = namespace + fixedVariables.getVariableNames().get(i);
      int curVarIndex = variables.getVariableByName(curVarName);

      if (assignment.contains(curVarIndex)) {
        int myVarNum = fixedVariables.getVariableNums().get(i);
        fixedAssignmentValues.put(myVarNum, assignment.getValue(curVarIndex));
      }
    }
    Assignment fixedAssignment = new Assignment(fixedAssignmentValues);

    List<List<DynamicAssignment>> allPlateAssignments = Lists.newArrayList();
    for (int i = 0; i < plateNames.size(); i++) {
      String plateName = plateNames.get(i);
      List<DynamicAssignment> plateAssignments = Lists.newArrayList();

      // Find replications of this plate.
      int j = 0;
      VariableNumMap plateVars = variables.getVariablesByNamePrefix(appendPlateToNamespace(
          namespace, plateName, j));
      while (plateVars.size() != 0) {
        Assignment subAssignment = assignment.intersection(plateVars);
        plateAssignments.add(plates.get(i).toDynamicAssignmentHelper(subAssignment,
            plateVars, appendPlateToNamespace(namespace, plateName, j)));

        // Advance to next replication.
        j++;
        plateVars = variables.getVariablesByNamePrefix(appendPlateToNamespace(namespace, plateName,
            j));
      }
      allPlateAssignments.add(plateAssignments);
    }
    return new DynamicAssignment(fixedAssignment, plateNames, allPlateAssignments);
  }

  private String appendPlateToNamespace(String namespace, String plateName, int repetitionIndex) {
    return namespace + plateName + NAMESPACE_SEPARATOR + repetitionIndex + NAMESPACE_SEPARATOR;
  }

  // //////////////////////////////////////////////////////////////
  // Methods for incrementally building DynamicVariableSets
  // //////////////////////////////////////////////////////////////

  /**
   * Returns a copy of {@code this} with an additional fixed variable. The
   * created variable is named {@code name} and has type {@code variable}.
   * 
   * @param name
   * @param variable
   * @return
   */
  public DynamicVariableSet addFixedVariable(String name, Variable variable) {
    int variableIndex = fixedVariableMaxInd + 1;
    return new DynamicVariableSet(fixedVariables.addMapping(variableIndex, name, variable),
        plateNames, plates, maximumReplications);
  }

  public DynamicVariableSet addPlate(String plateName, DynamicVariableSet plateVariables,
      int plateMaxReplications) {
    List<String> newPlateNames = Lists.newArrayList(plateNames);
    newPlateNames.add(plateName);
    List<DynamicVariableSet> newPlates = Lists.newArrayList(plates);
    newPlates.add(plateVariables);
    int[] newReplications = Arrays.copyOf(maximumReplications, maximumReplications.length + 1);
    newReplications[maximumReplications.length] = plateMaxReplications;
    return new DynamicVariableSet(fixedVariables, newPlateNames, newPlates, newReplications);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + fixedVariableMaxInd;
    result = prime * result + ((fixedVariables == null) ? 0 : fixedVariables.hashCode());
    result = prime * result + Arrays.hashCode(maximumReplications);
    result = prime * result + ((plateNames == null) ? 0 : plateNames.hashCode());
    result = prime * result + ((plates == null) ? 0 : plates.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    DynamicVariableSet other = (DynamicVariableSet) obj;
    if (fixedVariableMaxInd != other.fixedVariableMaxInd)
      return false;
    if (fixedVariables == null) {
      if (other.fixedVariables != null)
        return false;
    } else if (!fixedVariables.equals(other.fixedVariables))
      return false;
    if (!Arrays.equals(maximumReplications, other.maximumReplications))
      return false;
    if (plateNames == null) {
      if (other.plateNames != null)
        return false;
    } else if (!plateNames.equals(other.plateNames))
      return false;
    if (plates == null) {
      if (other.plates != null)
        return false;
    } else if (!plates.equals(other.plates))
      return false;
    return true;
  }
  
  @Override
  public String toString() {
    return "(" + fixedVariables.toString() + " plates: " + plateNames.toString() + " " + plates.toString() + ")";
  }
}
