package com.jayantkrish.jklol.p3;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;

public class KbState {
  
  private final IndexedList<String> typeNames;
  private final List<DiscreteVariable> typeVars;

  private final IndexedList<String> functionNames;
  private final List<FunctionAssignment> functionAssignments;
  private final List<FeatureVectorGenerator<FunctionAssignment>> featureGens;
  private final List<Tensor> featureVectors;
  
  private final Set<Integer> updated;

  public KbState(IndexedList<String> typeNames, List<DiscreteVariable> typeVars,
      IndexedList<String> functionNames, List<FunctionAssignment> functionAssignments,
      List<FeatureVectorGenerator<FunctionAssignment>> featureGens, List<Tensor> featureVectors,
      Set<Integer> updated) {
    this.typeNames = Preconditions.checkNotNull(typeNames);
    this.typeVars = Preconditions.checkNotNull(typeVars);
    this.functionNames = Preconditions.checkNotNull(functionNames);
    this.functionAssignments = Preconditions.checkNotNull(functionAssignments);
    Preconditions.checkArgument(functionNames.size() == functionAssignments.size());
    
    this.featureGens = Preconditions.checkNotNull(featureGens);
    this.featureVectors = Preconditions.checkNotNull(featureVectors);

    this.updated = Preconditions.checkNotNull(updated);
  }

  public Object getFunctionValue(String functionName, List<Object> args) {
    int index = functionNames.getIndex(functionName);
    return functionAssignments.get(index).getValue(args);
  }

  public KbState putFunctionValue(String functionName, List<Object> args, Object value) {
    int index = functionNames.getIndex(functionName);

    FunctionAssignment current = functionAssignments.get(index);
    FunctionAssignment next = current.putValue(args, value);
    
    return putAssignment(functionName, next);
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

  public KbState putAssignment(String functionName, FunctionAssignment assignment) {
    int index = functionNames.getIndex(functionName);
    return putAssignment(index, assignment);
  }

  public KbState putAssignment(int functionIndex, FunctionAssignment assignment) {
    List<FunctionAssignment> nextAssignments = Lists.newArrayList(functionAssignments);
    nextAssignments.set(functionIndex, assignment);
    
    List<Tensor> nextFeatures = null;
    if (featureGens.get(functionIndex) != null) {
      nextFeatures = Lists.newArrayList(featureVectors);
      nextFeatures.set(functionIndex, featureGens.get(functionIndex).apply(assignment));
    } else {
      nextFeatures = featureVectors;
    }

    Set<Integer> nextUpdated = Sets.newHashSet(updated);
    nextUpdated.add(functionIndex);

    return new KbState(typeNames, typeVars, functionNames, nextAssignments,
        featureGens, nextFeatures, nextUpdated);
  }

  public List<Tensor> getPredicateFeatures() {
    return featureVectors;
  }

  public Set<Integer> getUpdatedFunctionIndexes() {
    return updated;
  }

  public DiscreteVariable getTypeVar(String typeName) {
    return typeVars.get(typeNames.getIndex(typeName));
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