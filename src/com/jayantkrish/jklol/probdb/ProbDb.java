package com.jayantkrish.jklol.probdb;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.util.IndexedList;

public class ProbDb {
  
  private final IndexedList<String> tableNames;
  private final List<ProbDbTable> tables;
  
  public ProbDb(List<String> tableNames, List<ProbDbTable> tables) {
    this.tableNames = IndexedList.create(tableNames);
    this.tables = ImmutableList.copyOf(tables);    
    Preconditions.checkArgument(tableNames.size() == tables.size());
  }

	public ProbDbTable getTable(String tableName) {
	  int index = tableNames.getIndex(tableName);
	  if (index != -1) {
	    return tables.get(index);
	  } else {
	    return null;
	  }
	}
	
	public double getUnnormalizedLogProbability(DbAssignment dbAssignment) {
	  double logProb = 0;
	  for (int i = 0; i < tableNames.size(); i++) {
	    TableAssignment assignment = dbAssignment.getTable(tableNames.get(i));
	    logProb += tables.get(i).getUnnormalizedLogProbability(assignment);
	  }
	  return logProb;
	}
}
