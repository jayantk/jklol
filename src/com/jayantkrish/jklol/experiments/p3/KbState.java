package com.jayantkrish.jklol.experiments.p3;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.p3.FunctionAssignment;
import com.jayantkrish.jklol.util.IndexedList;

public class KbState {
  
  private final IndexedList<String> typeNames;
  private final List<DiscreteVariable> typeVars;

  private final IndexedList<String> functionNames;
  private final List<FunctionAssignment> functionAssignments;

  public KbState(IndexedList<String> typeNames, List<DiscreteVariable> typeVars,
      IndexedList<String> functionNames, List<FunctionAssignment> functionAssignments) {
    this.typeNames = Preconditions.checkNotNull(typeNames);
    this.typeVars = Preconditions.checkNotNull(typeVars);
    this.functionNames = Preconditions.checkNotNull(functionNames);
    this.functionAssignments = Preconditions.checkNotNull(functionAssignments);
    Preconditions.checkArgument(functionNames.size() == functionAssignments.size());
  }

  public Object getFunctionValue(String functionName, List<Object> args) {
    int index = functionNames.getIndex(functionName);
    if (functionAssignments.get(index) == null) {
      // TODO: figure out how to handle the unassigned case more generally.
      return ConstantValue.NIL;
    } else {
      return functionAssignments.get(index).getValue(args);
    }
  }

  public KbState putFunctionValue(String functionName, List<Object> args, Object value) {
    int index = functionNames.getIndex(functionName);
    
    FunctionAssignment current = functionAssignments.get(index);
    FunctionAssignment next = current.putValue(args, value);
    
    List<FunctionAssignment> nextAssignment = Lists.newArrayList(functionAssignments);
    nextAssignment.set(index, next);
    
    return new KbState(typeNames, typeVars, functionNames, nextAssignment);
  }

  public IndexedList<String> getFunctions() {
    return functionNames;
  }
  
  public List<FunctionAssignment> getAssignments() {
    return functionAssignments;
  }

  public DiscreteVariable getTypeVar(String typeName) {
    return typeVars.get(typeNames.getIndex(typeName));
  }

  /**
   * Returns {@code true} if this state is consistent {@code other}.
   * Two states are consistent if every category and relation instance
   * with an assigned truth value has the same truth value in both
   * states.
   * 
   * @param other
   * @return
   */
  public boolean isConsistentWith(KbState other) {
    // Both states need to have the same functions in the same order.
    // Don't check the names specifically though, because it's expensive.
    Preconditions.checkArgument(other.functionNames.size() == functionNames.size());
    
    List<FunctionAssignment> otherAssignments = other.getAssignments();
    for (int i = 0; i < functionAssignments.size(); i++) {
      FunctionAssignment a1 = functionAssignments.get(i);
      FunctionAssignment a2 = otherAssignments.get(i);
      if (a1 != null && a2 != null) {
        if (!a1.isConsistentWith(a2)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return functionNames.toString();
  }
}
