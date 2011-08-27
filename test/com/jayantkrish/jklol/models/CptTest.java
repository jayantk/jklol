package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A test of SparseCpts and their interactions with CptTableFactors.
 */
public class CptTest extends TestCase {

  private Cpt first;
  private Cpt second;
  
  private Cpt incompatible;
  
  private DiscreteVariable v;

  private Object[][] assignments;

  public void setUp() {
    v = new DiscreteVariable("Two values",
        Arrays.asList(new String[] { "T", "F" }));

    VariableNumMap parents = new VariableNumMap(Arrays.asList(new Integer[] { 0, 1 }), 
        Arrays.asList(new DiscreteVariable[] { v, v }));
    VariableNumMap children = new VariableNumMap(Arrays.asList(new Integer[] { 2, 3 }), 
        Arrays.asList(new DiscreteVariable[] { v, v }));
    VariableNumMap allVars = parents.union(children);
    
    VariableNumMap differentParents = new VariableNumMap(Arrays.asList(new Integer[] { 5 }), 
        Arrays.asList(new DiscreteVariable[] { v }));
    
    first = new Cpt(parents, children);
    second = new Cpt(parents, children);
    
    incompatible = new Cpt(differentParents, children);

    // Note: Parent F, T was unassigned!
    assignments = new Object[][] {{ "T", "T", "T", "T" },
        { "T", "T", "F", "T" },
        { "T", "F", "F", "T" },
        { "F", "F", "F", "F" },
        { "F", "F", "T", "T" }};
    for (int i = 0; i < assignments.length; i++) {
      first.incrementOutcomeCount(allVars.outcomeToAssignment(assignments[i]), 1.0);
    }
  }

  public void testGetProbability() {
    assertEquals(0.5, first.getProbability(first.getVars().outcomeToAssignment(assignments[0])));
    assertEquals(0.0, first.getProbability(first.getVars().outcomeToAssignment(
        new Object[] { "T", "T", "F", "F" })));
    assertEquals(1.0, first.getProbability(first.getVars().outcomeToAssignment(assignments[2])));
  }
  
  public void testIncrementConstant() {
    first.increment(1.0);

    assertEquals(2.0 / 6.0, first.getProbability(first.getVars().outcomeToAssignment(assignments[0])));
    assertEquals(1.0 / 6.0, first.getProbability(first.getVars().outcomeToAssignment(
        new Object[] { "T", "T", "F", "F" })));
    assertEquals(2.0 / 5.0, first.getProbability(first.getVars().outcomeToAssignment(assignments[2])));
  }
  
  public void testIncrementSufficientStats() {
    second.increment(1.0);
    first.increment(second, 2.0);
    assertEquals(3.0 / 10.0, first.getProbability(first.getVars().outcomeToAssignment(assignments[0])));
    assertEquals(2.0 / 10.0, first.getProbability(first.getVars().outcomeToAssignment(
        new Object[] { "T", "T", "F", "F" })));
    assertEquals(3.0 / 9.0, first.getProbability(first.getVars().outcomeToAssignment(assignments[2])));
  }
  
  public void testIncrementIncompatibleSufficientStats() {
    incompatible.increment(1.0);
    try {
      first.increment(incompatible, 1.0);
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }

  public void testUnsetParentError() {
    try {
      first.getProbability(first.getVars().outcomeToAssignment(
          new Object[] { "F", "T", "T", "T" }));
    } catch (ArithmeticException e) {
      return;
    }
    fail("Expected ArithmeticException");
  }

  public void testIteration() {
    Iterator<Assignment> iter = first.assignmentIterator();

    Set<Assignment> shouldBeInIter = new HashSet<Assignment>();
    for (int i = 0; i < assignments.length; i++) {
      shouldBeInIter.add(first.getVars().outcomeToAssignment(assignments[i]));
    }

    while (iter.hasNext()) {
      Assignment a = iter.next();
      assertTrue(shouldBeInIter.contains(a));
      shouldBeInIter.remove(a);
    }
    assertTrue(shouldBeInIter.isEmpty());
  }
}
