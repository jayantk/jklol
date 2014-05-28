package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;

public class LambdaValue {
  private final List<SExpression> argumentExpressions;
  private final int[] argumentNameIndexes;
  private final SExpression body;
  private final Environment parentEnvironment;

  public LambdaValue(List<SExpression> argumentExpressions, int[] argumentNameIndexes,
      SExpression body, Environment parentEnvironment) {
    this.argumentExpressions = argumentExpressions;
    this.argumentNameIndexes = argumentNameIndexes;
    this.body = Preconditions.checkNotNull(body);
    this.parentEnvironment = Preconditions.checkNotNull(parentEnvironment);
  }
  
  public List<SExpression> getArgumentExpressions() {
    return argumentExpressions;
  }

  public int[] getArgumentNameIndexes() {
    return argumentNameIndexes;
  }

  public SExpression getBody() {
    return body;
  }

  public Environment getEnvironment() {
    return parentEnvironment;
  }
  
  @Override
  public String toString() {
    return "[lambda procedure: " + body + "]";
  }
}
