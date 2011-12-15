package com.jayantkrish.jklol.models.dynamic;

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

public class DynamicVariableSet {

  private final VariableNumMap fixedVariables;
  private final List<String> plateNames;
  private final List<DynamicVariableSet> plates;
  
  private static final String NAMESPACE_SEPARATOR = "/";
  
  public static final DynamicVariableSet EMPTY = new DynamicVariableSet(VariableNumMap.emptyMap(), 
      Collections.<String>emptyList(), Collections.<DynamicVariableSet>emptyList());
  
  public DynamicVariableSet(VariableNumMap fixedVariables, 
      List<String> plateNames, List<DynamicVariableSet> plates) {
    Preconditions.checkArgument(plateNames.size() == plates.size());
    this.fixedVariables = fixedVariables;
    this.plateNames = plateNames;
    this.plates = plates;
  }
  
  public static DynamicVariableSet fromVariables(VariableNumMap variables) {
    return new DynamicVariableSet(variables, Collections.<String>emptyList(),
        Collections.<DynamicVariableSet>emptyList());
  }
  
  public DynamicAssignment outcomeToAssignment(List<? extends Object> values) {
    return DynamicAssignment.fromAssignment(fixedVariables.outcomeToAssignment(values));
  }
  
  public DynamicAssignment outcomeToAssignment(Object... values) {
    Preconditions.checkArgument(values.length == fixedVariables.size() + plateNames.size());
    return DynamicAssignment.fromAssignment(fixedVariables.outcomeToAssignment(values));
  }
  
  public DynamicAssignment plateOutcomeToAssignment(List<DynamicAssignment> ... plateValues) {
    Preconditions.checkArgument(plateValues.length == plateNames.size());
    List<List<DynamicAssignment>> plateAssignments = Arrays.asList(plateValues);
    return new DynamicAssignment(Assignment.EMPTY, plateNames, plateAssignments);
  }
  
  public VariableNumMap getFixedVariables() {
    return fixedVariables;
  }
  
  public DynamicVariableSet getPlate(String plateName) {
    int index = plateNames.indexOf(plateName);
    Preconditions.checkArgument(index != -1);
    return plates.get(index);
  }

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
   * If two assignments have the same number of replications of each plate, this
   * method will return the same {@code VariableNumMap}.
   * 
   * @param assignment
   * @return
   */
  public VariableNumMap instantiateVariables(DynamicAssignment assignment) {
    List<String> varNames = Lists.newArrayList();
    List<Variable> variables = Lists.newArrayList();
    
    instantiateVariablesHelper(assignment, varNames, variables, "");
    Preconditions.checkState(varNames.size() == variables.size());
    return VariableNumMap.fromVariableNames(varNames, variables);
  }
  
  private void instantiateVariablesHelper(DynamicAssignment assignment, 
      List<String> variableNames, List<Variable> variables, String namespace) {
    for (String varName : fixedVariables.getVariableNames()) {
      variableNames.add(namespace + varName);
      variables.add(fixedVariables.getVariable(fixedVariables.getVariableByName(varName)));
    }
    
    for (int i = 0; i < plateNames.size(); i++) {
      Preconditions.checkArgument(assignment.containsPlateValue(plateNames.get(i)),
          "Cannot assign: " + assignment + " to: " + this);
      List<DynamicAssignment> plateValues = assignment.getPlateValue(plateNames.get(i));
      for (int j = 0; j < plateValues.size(); j++) {
        plates.get(i).instantiateVariablesHelper(plateValues.get(j), 
            variableNames, variables, appendPlateToNamespace(namespace, plateNames.get(i), j)); 
      }
    }
  }

  public Assignment toAssignment(DynamicAssignment assignment) {
    Map<Integer, Object> values = Maps.newHashMap();
    toAssignmentHelper(assignment, 0, values);
    return new Assignment(values);
  }
  
  private int toAssignmentHelper(DynamicAssignment assignment, int varNumOffset, 
      Map<Integer, Object> values) {
    Assignment fixedAssignment = assignment.getFixedAssignment(); 
    Preconditions.checkArgument(fixedVariables.containsAll(fixedAssignment.getVariableNums()), 
        "Cannot assign: " + assignment + " to: " + this);
    
    for (int i = 0; i < fixedVariables.size(); i++) {
      int curVarNum = fixedVariables.getVariableNums().get(i);
      if (fixedAssignment.contains(curVarNum)) {
        values.put(varNumOffset + i, fixedAssignment.getValue(curVarNum));
      }
    }
    
    int curOffset = varNumOffset + fixedVariables.size();
    for (int i = 0; i < plateNames.size(); i++) {
      Preconditions.checkArgument(assignment.containsPlateValue(plateNames.get(i)),
          "Cannot assign: " + assignment + " to: " + this);
      for (DynamicAssignment plateValue : assignment.getPlateValue(plateNames.get(i))) {
        curOffset = plates.get(i).toAssignmentHelper(plateValue, curOffset, values);
      }
    }
    return curOffset;
  }
  
  public DynamicAssignment toDynamicAssignment(Assignment assignment, VariableNumMap variables) {
    return toDynamicAssignmentHelper(assignment, variables, "");
  }
  
  private DynamicAssignment toDynamicAssignmentHelper(Assignment assignment, VariableNumMap variables, String namespace) {
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
      VariableNumMap plateVars = variables.getVariablesByNamePrefix(appendPlateToNamespace(namespace, plateName, j));
      while (plateVars.size() != 0) {
        Assignment subAssignment = assignment.intersection(plateVars);
        plateAssignments.add(plates.get(i).toDynamicAssignmentHelper(subAssignment, 
            plateVars, appendPlateToNamespace(namespace, plateName, j)));
        
        // Advance to next replication.
        j++;
        plateVars = variables.getVariablesByNamePrefix(appendPlateToNamespace(namespace, plateName, j));
      }
      allPlateAssignments.add(plateAssignments);
    }
    return new DynamicAssignment(fixedAssignment, plateNames, allPlateAssignments);
  }
  
  private String appendPlateToNamespace(String namespace, String plateName, int repetitionIndex) {
    return namespace + plateName + NAMESPACE_SEPARATOR + repetitionIndex + NAMESPACE_SEPARATOR; 
  }
  
  ////////////////////////////////////////////////////////////////
  // Methods for incrementally building DynamicVariableSets
  ////////////////////////////////////////////////////////////////
  
  public DynamicVariableSet addFixedVariable(String name, Variable variable) {
    // TODO: this index could be taken. Solution: enforce that fixedVariables contains
    // indices from 0 to fixedVariables.size()-1.
    int variableIndex = fixedVariables.size();
    return new DynamicVariableSet(fixedVariables.addMapping(variableIndex, name, variable),
        plateNames, plates);
  }
  
  public DynamicVariableSet addPlate(String plateName, DynamicVariableSet plateVariables) {
    List<String> newPlateNames = Lists.newArrayList(plateNames);
    newPlateNames.add(plateName);
    List<DynamicVariableSet> newPlates = Lists.newArrayList(plates);
    newPlates.add(plateVariables);
    return new DynamicVariableSet(fixedVariables, newPlateNames, newPlates);
  }
  
  @Override
  public int hashCode() {
    return fixedVariables.hashCode() * 7238123 + plateNames.hashCode() * 783 + plates.hashCode();  
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (other instanceof DynamicVariableSet) {
      DynamicVariableSet otherVariables = (DynamicVariableSet) other;
      return otherVariables.fixedVariables.equals(this.fixedVariables) && 
          otherVariables.plateNames.equals(this.plateNames) &&
          otherVariables.plates.equals(this.plates);
    }
    return false;
  }
  
  @Override
  public String toString() {
    return "(" + fixedVariables.toString() + " plates: " + plateNames.toString() + ")";  
  }
}
