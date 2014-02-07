package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class LambdaValue {
  private final List<String> argumentNames;
  private final SExpression body;
  private final Environment parentEnvironment;

  public LambdaValue(List<String> argumentNames, SExpression body, Environment parentEnvironment) {
    this.argumentNames = ImmutableList.copyOf(argumentNames);
    this.body = Preconditions.checkNotNull(body);
    this.parentEnvironment = Preconditions.checkNotNull(parentEnvironment);
  }
  
  public List<String> getArgumentNames() {
    return argumentNames;
  }
  
  public SExpression getBody() {
    return body;
  }

  public Environment getEnvironment() {
    return parentEnvironment;
  }
}
