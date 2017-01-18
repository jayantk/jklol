package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Type;

/**
 * A mapping from type variables to replacement types.
 * 
 * @author jayantk
 *
 */
public class TypeReplacement {

  private final Map<Integer, Type> bindings;

  public TypeReplacement(Map<Integer, Type> bindings) {
    this.bindings = Maps.newHashMap(bindings);
  }
  
  public TypeReplacement(TypeReplacement other) {
    this.bindings = Maps.newHashMap(other.bindings);
  }
  
  /**
   * Compose (var -> t) with this replacement. Any occurrences
   * of var in this replacement are substituted with t.
   *
   * @param var
   * @param t
   */
  public void add(int var, Type t) {
    Set<Integer> tVars = t.getTypeVariables();
    Set<Integer> intersection = Sets.intersection(tVars, bindings.keySet());
    Preconditions.checkArgument(intersection.size() == 0,
        "Tried to replace %s with %s, but already have bindings for %s", var, t, intersection);
    Preconditions.checkArgument(!tVars.contains(var),
        "Recursive replacement: tried to replace %s with %s", var, t);
    
    Map<Integer, Type> newBinding = Maps.newHashMap();
    newBinding.put(var, t);
    for (int boundVar : bindings.keySet()) {
      bindings.put(boundVar, bindings.get(boundVar).substitute(newBinding));
    }
    bindings.put(var, t);
  }
  
  /**
   * Returns {@code true} if this contains a replacement for
   * {@code var}
   *  
   * @param var
   * @return
   */
  public boolean contains(int var) {
    return bindings.containsKey(var);
  }
  
  public Map<Integer, Type> getBindings() {
    return bindings;
  }
  
  @Override
  public String toString() {
    return bindings.toString();
  }
}
