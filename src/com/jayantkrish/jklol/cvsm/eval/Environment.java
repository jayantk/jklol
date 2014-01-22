package com.jayantkrish.jklol.cvsm.eval;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;

public class Environment {

  private final Map<String, Object> boundVariables;
  private final ParametricFactorGraphBuilder factorGraphBuilder;

  private final Environment parentEnvironment;

  public Environment(Map<String, Object> bindings, ParametricFactorGraphBuilder factorGraphBuilder,
      Environment parentEnvironment) {
    this.boundVariables = Preconditions.checkNotNull(bindings);
    this.factorGraphBuilder = Preconditions.checkNotNull(factorGraphBuilder);

    this.parentEnvironment = parentEnvironment;
  }

  public static Environment empty() {
    return Environment.empty(null);
  }

  public static Environment empty(Environment parentEnvironment) {
    return new Environment(Maps.<String, Object>newHashMap(), new ParametricFactorGraphBuilder(),
        parentEnvironment);
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
    return factorGraphBuilder;
  }
}
