package com.jayantkrish.jklol.probdb;

public interface Query {
  
  public boolean isTerminal();
  
  public TableAssignment evaluate(DbAssignment db);
}
