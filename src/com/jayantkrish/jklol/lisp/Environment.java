package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class Environment {

  private final Map<String, Object> boundVariables;
  private final Environment parentEnvironment;

  public Environment(Map<String, Object> bindings, Environment parentEnvironment) {
    this.boundVariables = Preconditions.checkNotNull(bindings);
    this.parentEnvironment = parentEnvironment;
  }

  public static Environment empty() {
    return Environment.extend(null);
  }

  public static Environment extend(Environment parentEnvironment) {
    return new Environment(Maps.<String, Object> newHashMap(), parentEnvironment);
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
    Object value = parentEnvironment.getValue(name);
    return value;
  }
}
