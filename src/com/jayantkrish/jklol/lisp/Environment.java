package com.jayantkrish.jklol.lisp;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.util.IndexedList;

public class Environment {

  private final Map<Integer, Object> bindings;
  private final Environment parentEnvironment;

  public Environment(Map<Integer, Object> bindings, Environment parentEnvironment) {
    this.bindings = Preconditions.checkNotNull(bindings);
    this.parentEnvironment = parentEnvironment;
  }

  public static Environment empty() {
    return Environment.extend(null);
  }

  public static Environment extend(Environment parentEnvironment) {
    return new Environment(Maps.<Integer, Object> newHashMap(), parentEnvironment);
  }

  public void bindName(String name, Object value, IndexedList<String> symbolTable) {
    if (!symbolTable.contains(name)) {
      symbolTable.add(name);
    }
    int index = symbolTable.getIndex(name);
    bindings.put(index, value);
  }

  public void bindName(int nameIndex, Object value) {
    bindings.put(nameIndex, value);
  }

  public void bindNames(List<String> names, List<Object> values,
      IndexedList<String> symbolTable) {
    Preconditions.checkArgument(names.size() == values.size());
    for (int i = 0; i < names.size(); i++) {
      bindName(names.get(i), values.get(i), symbolTable);
    }
  }

  public void bindNames(int[] nameIndexes, List<Object> values) {
    Preconditions.checkArgument(nameIndexes.length == values.size());
    for (int i = 0; i < nameIndexes.length; i++) {
      bindings.put(nameIndexes[i], values.get(i));
    }
  }

  public Object getValue(String name, IndexedList<String> symbolTable) {
    int index = symbolTable.getIndex(name);
    return getValue(index, symbolTable);
  }

  public Object getValue(int symbolIndex, IndexedList<String> symbolTable) {
    if (bindings.containsKey(symbolIndex)) {
      return bindings.get(symbolIndex);
    }

    Preconditions.checkState(parentEnvironment != null,
        "Tried accessing unbound variable: %s", symbolTable.get(symbolIndex));
    Object value = parentEnvironment.getValue(symbolIndex, symbolTable);
    return value;
  }
}
