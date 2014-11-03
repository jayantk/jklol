package com.jayantkrish.jklol.probdb;

import java.util.List;

import com.google.common.collect.ImmutableList;

public abstract class AbstractQuery implements Query {

  private final List<Query> subQueries;
  
  public AbstractQuery(List<Query> subQueries) {
    this.subQueries = ImmutableList.copyOf(subQueries);
  }
  
  @Override
  public boolean isTerminal() {
    return subQueries.size() != 0;
  }
}
