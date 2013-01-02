package com.jayantkrish.jklol.probdb;

import java.util.Collections;

import com.google.common.base.Preconditions;

/**
 * Database query that returns the set of tuples from a particular table.
 * 
 * @author jayant
 *
 */
public class TableQuery extends AbstractQuery {
  
  private final String tableName;
  
  public TableQuery(String tableName) {
    super(Collections.<Query>emptyList());
    this.tableName = Preconditions.checkNotNull(tableName);
  }

  @Override
  public TableAssignment evaluate(DbAssignment db) {
    TableAssignment assignment = db.getTable(tableName);
    Preconditions.checkState(assignment != null);
    return assignment;
  }
}
