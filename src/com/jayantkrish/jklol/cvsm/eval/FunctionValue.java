package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class FunctionValue implements Value {
  private final List<String> argumentNames;
  private final SExpression body;
  private final Environment parentEnvironment;

  public FunctionValue(List<String> argumentNames, SExpression body, Environment parentEnvironment) {
    this.argumentNames = ImmutableList.copyOf(argumentNames);
    this.body = Preconditions.checkNotNull(body);
    this.parentEnvironment = Preconditions.checkNotNull(parentEnvironment);
  }

  public Value apply(List<Value> argumentValues, Eval eval) {
    Preconditions.checkArgument(argumentValues.size() == argumentNames.size(),
        "Wrong number of arguments: expected %s, got %s", argumentNames, argumentValues);

    Environment boundEnvironment = Environment.empty(parentEnvironment);
    boundEnvironment.bindNames(argumentNames, argumentValues);

    return eval.eval(body, boundEnvironment).getValue();
  }
}
