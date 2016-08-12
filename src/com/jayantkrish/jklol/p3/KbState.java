package com.jayantkrish.jklol.p3;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.util.IndexedList;

public class KbState {
  
  private final IndexedList<String> typeNames;
  private final List<DiscreteVariable> typeVars;

  private final IndexedList<String> functionNames;
  private final List<FunctionAssignment> functionAssignments;

  private final Set<Integer> updated;

  public KbState(IndexedList<String> typeNames, List<DiscreteVariable> typeVars,
      IndexedList<String> functionNames, List<FunctionAssignment> functionAssignments,
      Set<Integer> updated) {
    this.typeNames = Preconditions.checkNotNull(typeNames);
    this.typeVars = Preconditions.checkNotNull(typeVars);
    this.functionNames = Preconditions.checkNotNull(functionNames);
    this.functionAssignments = Preconditions.checkNotNull(functionAssignments);
    Preconditions.checkArgument(functionNames.size() == functionAssignments.size());

    this.updated = Preconditions.checkNotNull(updated);
  }
  
  public static Supplier<KbState> getNullSupplier(final IndexedList<String> typeNames,
      final List<DiscreteVariable> typeVars, final IndexedList<String> functionNames) {
    return new Supplier<KbState>() {
      @Override
      public KbState get() {
        return new KbState(typeNames, typeVars, functionNames,
            Lists.newArrayList(Collections.nCopies(functionNames.size(), null)), Sets.newHashSet());
      }
    };
  }
  
  public IndexedList<String> getTypeNames() {
    return typeNames;
  }
  
  public List<DiscreteVariable> getTypeVars() {
    return typeVars;
  }

  public Object getFunctionValue(String functionName, List<Object> args) {
    int index = functionNames.getIndex(functionName);
    return functionAssignments.get(index).getValue(args);
  }

  public void putFunctionValue(String functionName, List<Object> args, Object value) {
    int index = functionNames.getIndex(functionName);

    FunctionAssignment current = functionAssignments.get(index);
    current.putValue(args, value);
    updated.add(index);
  }
  
  public void putFunctionValue(int index, List<Object> args, Object value) {
    FunctionAssignment current = functionAssignments.get(index);
    current.putValue(args, value);
    updated.add(index);
  }

  public IndexedList<String> getFunctions() {
    return functionNames;
  }
  
  public List<FunctionAssignment> getAssignments() {
    return functionAssignments;
  }

  public FunctionAssignment getAssignment(String functionName) {
    return functionAssignments.get(functionNames.getIndex(functionName));
  }

  public FunctionAssignment getAssignment(int functionIndex) {
    return functionAssignments.get(functionIndex);
  }

  public void setAssignment(int functionIndex, FunctionAssignment assignment) {
    functionAssignments.set(functionIndex, assignment);
  }

  public void clear() {
    for (int i = 0; i < functionAssignments.size(); i++) {
      functionAssignments.set(i, null);
    }
    updated.clear();
  }

  public Set<Integer> getUpdatedFunctionIndexes() {
    return updated;
  }
  
  public void setUpdated(int functionIndex) {
    updated.add(functionIndex);
  }

  public DiscreteVariable getTypeVar(String typeName) {
    return typeVars.get(typeNames.getIndex(typeName));
  }
  
  public KbState copy() {
    List<FunctionAssignment> assignmentsCopy = Lists.newArrayList();
    for (FunctionAssignment a : functionAssignments) {
      assignmentsCopy.add(a.copy());
    }

    return new KbState(typeNames, typeVars, functionNames, assignmentsCopy,
        Sets.newHashSet(updated));
  }
  
  public KbState shallowCopy() {
    return new KbState(typeNames, typeVars, functionNames,
        Lists.newArrayList(functionAssignments), Sets.newHashSet(updated));
  }
  
  public void shallowCopyTo(KbState other) {
    List<FunctionAssignment> otherAssignments = other.functionAssignments;
    Preconditions.checkArgument(otherAssignments.size() == functionAssignments.size());
    int numAssignments = otherAssignments.size();
    
    for (int i = 0; i < numAssignments; i++) {
      otherAssignments.set(i, functionAssignments.get(i));
    }

    other.updated.clear();
    other.updated.addAll(updated);
  }

  /**
   * Returns {@code true} if this state is consistent {@code other}.
   * Two states are consistent if every assigned function value is
   * equal.
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
      if (!a1.isConsistentWith(a2)) {
        return false;
      }
    }
    return true;
  }
  
  /**
   * Returns {@code true} if every function value in {@code this}
   * equals that in {@code other}.
   *  
   * @param other
   * @return
   */
  public boolean isEqualTo(KbState other) {
    // Both states need to have the same functions in the same order.
    // Don't check the names specifically though, because it's expensive.
    Preconditions.checkArgument(other.functionNames.size() == functionNames.size());
    
    List<FunctionAssignment> otherAssignments = other.getAssignments();
    for (int i = 0; i < functionAssignments.size(); i++) {
      FunctionAssignment a1 = functionAssignments.get(i);
      FunctionAssignment a2 = otherAssignments.get(i);
      if (!a1.isEqualTo(a2)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return functionNames.toString();
  }
}
