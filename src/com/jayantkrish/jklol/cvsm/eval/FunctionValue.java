package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class FunctionValue implements Value {
  private final List<String> argumentNames;
  private final SExpression body;
  private final Environment environment;

  public FunctionValue(List<String> argumentNames, SExpression body, Environment environment) {
    this.argumentNames = ImmutableList.copyOf(argumentNames);
    this.body = Preconditions.checkNotNull(body);
    this.environment = Preconditions.checkNotNull(environment);
  }

  public Value apply(List<Value> argumentValues, Eval eval) {
    Preconditions.checkArgument(argumentValues.size() == argumentNames.size(),
        "Wrong number of arguments: expected %s, got %s", argumentNames, argumentValues);
    
    Environment boundEnvironment = environment.bindNames(argumentNames, argumentValues);
    
    return eval.eval(body, boundEnvironment).getValue();
  }
}
