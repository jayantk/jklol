package com.jayantkrish.jklol.probdb;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Tensor;

public class ProbDbTable {
  private final VariableNumMap indexVars;

  // Stores the log unnormalized probability of each possible tuple 
  // belonging to the table.
  private final Tensor logWeights;
  
  public ProbDbTable(VariableNumMap indexVars, Tensor logWeights) {
    this.indexVars = Preconditions.checkNotNull(indexVars);
    this.logWeights = Preconditions.checkNotNull(logWeights);
    
    Preconditions.checkArgument(Arrays.equals(indexVars.getVariableNumsArray(),
        logWeights.getDimensionNumbers()));
  }

  public VariableNumMap getIndexVariables() {
    return indexVars;
  }
  
  public double getUnnormalizedLogProbability(TableAssignment assignment) {
    Preconditions.checkArgument(assignment.getVariables().equals(indexVars));
    return logWeights.elementwiseProduct(assignment.getIndicators())
        .sumOutDimensions(logWeights.getDimensionNumbers()).getByDimKey();
  }
}
