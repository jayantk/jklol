package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;

public class Environment {

  private final Map<String, Object> boundVariables;

  // The factor graph being built by the program execution.
  private final ParametricFactorGraphBuilder factorGraphBuilder;
  private VariableNumMap variablesToEliminate;

  private final Environment parentEnvironment;

  public Environment(Map<String, Object> bindings, ParametricFactorGraphBuilder factorGraphBuilder,
      VariableNumMap variablesToEliminate, Environment parentEnvironment) {
    this.boundVariables = Preconditions.checkNotNull(bindings);
    this.factorGraphBuilder = Preconditions.checkNotNull(factorGraphBuilder);
    this.variablesToEliminate = Preconditions.checkNotNull(variablesToEliminate);

    this.parentEnvironment = parentEnvironment;
  }

  public static Environment empty() {
    return Environment.empty(null);
  }

  public static Environment empty(Environment parentEnvironment) {
    return new Environment(Maps.<String, Object>newHashMap(), new ParametricFactorGraphBuilder(),
        VariableNumMap.EMPTY, parentEnvironment);
  }

  public void bindName(String name, Object value) {
    bindNames(Arrays.asList(name), Arrays.asList(value));
  }

  public void bindNames(List<String> names, List<Object> values) {
    Preconditions.checkArgument(names.size() == values.size());
    for (int i = 0; i < names.size(); i++) {
      boundVariables.put(names.get(i), values.get(i));
    }
  }

  public Object getValue(String name) {
    if (boundVariables.containsKey(name)) {
      return boundVariables.get(name);
    }

    Preconditions.checkState(parentEnvironment != null,
        "Tried accessing unbound variable: %s", name);
    return parentEnvironment.getValue(name);
  }

  public ParametricFactorGraphBuilder getFactorGraphBuilder() {
    if (parentEnvironment == null) {
      return factorGraphBuilder;
    } else {
      return parentEnvironment.getFactorGraphBuilder();
    }
  }

  public void addVariableToEliminate(VariableNumMap var) {
    if (parentEnvironment == null) {
      this.variablesToEliminate = variablesToEliminate.union(var);
    } else {
      parentEnvironment.addVariableToEliminate(var);
    }
  }
}
