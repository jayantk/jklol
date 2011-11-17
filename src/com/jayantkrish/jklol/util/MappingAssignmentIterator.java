package com.jayantkrish.jklol.util;

import java.util.Iterator;
import java.util.Map;

public class MappingAssignmentIterator implements Iterator<Assignment> {

  private final Iterator<Assignment> baseIterator;
  private final Map<Integer, Integer> varNumMap;

  /**
   * Creates an iterator which gets each element of {@code baseIterator}, and
   * returns the result of calling
   * {@link Assignment#mapVariables(varNumMap)} on it.
   * 
   * @param baseIterator
   * @param varNumMap
   */
  public MappingAssignmentIterator(Iterator<Assignment> baseIterator,
      Map<Integer, Integer> varNumMap) {
    this.baseIterator = baseIterator;
    this.varNumMap = varNumMap;
  }

  @Override
  public boolean hasNext() {
    return baseIterator.hasNext();
  }

  @Override
  public Assignment next() {
    return baseIterator.next().mapVariables(varNumMap);
  }

  @Override
  public void remove() {
    baseIterator.remove();
  }
}
