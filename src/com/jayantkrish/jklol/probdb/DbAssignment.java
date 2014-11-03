package com.jayantkrish.jklol.probdb;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A deterministic database, i.e., a set of tuples. Such a 
 * database is an assignment to the variables of a {@code ProbDb}.
 *  
 * @author jayant
 */
public class DbAssignment {

  private final IndexedList<String> tableNames;
  private final List<TableAssignment> tableAssignments;
  
  public DbAssignment(List<String> tableNames, List<TableAssignment> tableAssignments) {
    this.tableNames = IndexedList.create(tableNames);
    this.tableAssignments = ImmutableList.copyOf(tableAssignments);
  }

  public TableAssignment getTable(String name) {
    int index = tableNames.getIndex(name);
    if (index != -1) {
      return tableAssignments.get(index);
    } else {
      return null;
    }
  }
}
