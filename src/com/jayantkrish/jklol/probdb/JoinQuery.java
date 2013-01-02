package com.jayantkrish.jklol.probdb;

import java.util.Arrays;

import com.jayantkrish.jklol.tensor.Tensor;

public class JoinQuery extends AbstractQuery {
  private final Query parentQuery;
  private final Query childQuery;
  private final int[] childRelabeling;

  public JoinQuery(Query parentQuery, Query childQuery, int[] childRelabeling) {
    super(Arrays.asList(parentQuery, childQuery));
    this.parentQuery = parentQuery;
    this.childQuery = childQuery;
    this.childRelabeling = childRelabeling;
  }

  @Override
  public TableAssignment evaluate(DbAssignment db) {
    TableAssignment parentAssignment = parentQuery.evaluate(db);
    TableAssignment childAssignment = childQuery.evaluate(db).relabelVariables(childRelabeling);
    
    Tensor resultIndicators = parentAssignment.getIndicators().elementwiseProduct(
        childAssignment.getIndicators());
    return new TableAssignment(parentAssignment.getVariables(), resultIndicators);
  }
}
