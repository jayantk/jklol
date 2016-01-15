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
  
  /**
   * Copy constructor. If {@code env} has a parent environment,
   * that environment (and its parents) are recursively copied
   * as well.
   * 
   * @param env
   */
  public Environment(Environment env) {
    this.bindings = Maps.newHashMap(env.bindings);
    if (env.parentEnvironment != null) {
      this.parentEnvironment = new Environment(env.parentEnvironment);
    } else {
      this.parentEnvironment = null;
    }
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
  
  /**
   * Removes a binding from this environment. Returns the value
   * bound to that name if the name was bound, otherwise returns
   * {@code null}. 
   * 
   * @param name
   * @param symbolTable
   * @return
   */
  public Object unbindName(String name, IndexedList<String> symbolTable) {
    if (!symbolTable.contains(name)) {
      return null;
    }
    int index = symbolTable.getIndex(name);
    return bindings.remove(index);
  }
  
  public Object unbindName(int nameIndex) {
    return bindings.remove(nameIndex);
  }

  public Object getValue(String name, IndexedList<String> symbolTable) {
    int index = symbolTable.getIndex(name);
    return getValue(index, symbolTable);
  }

  public Object getValue(int symbolIndex, IndexedList<String> symbolTable) {
    if (bindings.containsKey(symbolIndex)) {
      return bindings.get(symbolIndex);
    }

    LispUtil.checkState(parentEnvironment != null,
        "Tried accessing unbound variable: %s", symbolTable.get(symbolIndex));
    Object value = parentEnvironment.getValue(symbolIndex, symbolTable);
    return value;
  }
}
