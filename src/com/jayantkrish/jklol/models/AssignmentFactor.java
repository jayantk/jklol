package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.util.Assignment;

public class AssignmentFactor extends AbstractFactor {
  
  private final Assignment baseAssignment;
  private final Factor baseFactor;
  
  public AssignmentFactor(Assignment baseAssignment, Factor baseFactor) {
    super(baseFactor.getVars().union(assignment.getVars()));
    // the variables in baseAssignment and baseFactor must be disjoint.
    Preconditions.checkArgument(!baseFactor.getVars().containsAny(baseAssignment.getVarNumsSorted()));
    
    this.baseAssignment = baseAssignment;
    this.baseFactor = baseFactor;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsVars(getVars().getVariableNums()));
    
    Assignment baseAssignmentComponent = assignment.subAssignment(baseAssignment.getVarNumsSorted());
    if (!baseAssignmentComponent.equals(baseAssignment)) {
      return 0;
    }
    return baseFactor.getUnnormalizedProbability(assignment);    
  }

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Factor conditional(Assignment assignment) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Factor add(Factor other) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Factor maximum(Factor other) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Factor product(Factor other) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Factor product(double constant) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Factor inverse() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public double size() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public Assignment sample() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public double computeExpectation(FeatureFunction feature) {
    // TODO Auto-generated method stub
    return 0;
  }

}
