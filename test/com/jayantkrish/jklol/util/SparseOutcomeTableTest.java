package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;

public class SparseOutcomeTableTest extends TestCase {

  private List<Integer> varNums;
  private SparseOutcomeTable<Double> table;

  private Assignment a1, a2;

  @Override
  public void setUp() {
    varNums = Arrays.asList(1, 3, 4);
    table = new SparseOutcomeTable<Double>(varNums);

    a1 = new Assignment(varNums, Arrays.asList(0, 0, 0));
    a2 = new Assignment(varNums, Arrays.asList(0, 2, 3));

    table.put(a1, 1.0);
    table.put(a2, 2.0);
  }

  public void testGetVarNums() {
    assertEquals(varNums, table.getVarNums());
  }

  public void testGet() {
    assertEquals(1.0, table.get(a1));
    assertEquals(2.0, table.get(a2));

    assertEquals(null, table.get(new Assignment(varNums, Arrays.asList(0, 0, 3))));
    try {
      table.get(new Assignment(Arrays.asList(0, 1, 2), Arrays.asList(0, 0, 0)));
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException.");
  }
  
  public void testSize() {
    assertEquals(2, table.size());
  }
  
  public void testGetKeysWithVariableValue() {
    assertEquals(Sets.newHashSet(a1), table.getKeysWithVariableValue(3, Sets.<Object>newHashSet(0)));
    assertEquals(Sets.newHashSet(a1, a2), table.getKeysWithVariableValue(1, Sets.<Object>newHashSet(0)));
    
    assertEquals(Sets.newHashSet(), table.getKeysWithVariableValue(1, Sets.<Object>newHashSet(7)));
    
    try {
      table.getKeysWithVariableValue(7, Sets.<Object>newHashSet(7));
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException.");
  }

  public void testAssignmentIterator() {
    Set<Assignment> expectedAssignments = Sets.newHashSet(a1, a2);
    assertEquals(expectedAssignments, Sets.newHashSet(table.assignmentIterator()));
  }
}
