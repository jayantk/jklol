package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.util.Assignment;

public class CptFactorTest extends TestCase {

  CptTableFactor f;  
  VariableNumMap cptVars;
  
  private Object[][] assignments;
  Set<Assignment> factorAssignments;
  
  public void setUp() {
    DiscreteVariable v = new DiscreteVariable("Two values",
        Arrays.asList("T", "F" ));
    
    VariableNumMap parents = new VariableNumMap(Arrays.asList(2, 3), 
        Arrays.asList(v, v ));
    VariableNumMap children = new VariableNumMap(Arrays.asList(4, 5), 
        Arrays.asList(v, v));
            
    BiMap<Integer, Integer> map = HashBiMap.create();
    for (int i = 0; i < 4; i++) {
      map.put(i + 2, i);
    }
    
    cptVars = new VariableNumMap(Arrays.asList(0, 1, 2, 3),
        Arrays.asList(v, v, v, v)); 
    
    f = new CptTableFactor(parents, children, map);
    
    // Note: Parent F, T was unassigned!
    assignments = new Object[][] {{ "T", "T", "T", "T" },
        { "T", "T", "F", "T" },
        { "T", "F", "F", "T" },
        { "F", "F", "F", "F" },
        { "F", "F", "T", "T" }};
    factorAssignments = Sets.newHashSet();
    for (int i = 0; i < assignments.length; i++) {
      f.getCurrentParameters().incrementOutcomeCount(cptVars.outcomeToAssignment(assignments[i]), 1.0);
      factorAssignments.add(f.getVars().outcomeToAssignment(assignments[i]));
    }
  }
  
  public void testGetUnnormalizedProbability() {
    assertEquals(0.5, f.getUnnormalizedProbability(f.getVars().outcomeToAssignment(assignments[0])));
    assertEquals(0.0, f.getUnnormalizedProbability(f.getVars().outcomeToAssignment(
        new Object[] { "T", "T", "F", "F" })));
    assertEquals(1.0, f.getUnnormalizedProbability(f.getVars().outcomeToAssignment(assignments[2])));
  }
  
  public void testGetNewSufficientStatistics() {
    Cpt newStats = f.getNewSufficientStatistics();
    assertEquals(f.getCurrentParameters().getParents(), newStats.getParents());
    assertEquals(f.getCurrentParameters().getChildren(), newStats.getChildren());
    assertEquals(f.getCurrentParameters().getVars(), newStats.getVars());
    
    // All assignments should have a count of 0.
    assertFalse(newStats.assignmentIterator().hasNext());
  }
  
  public void testGetSufficientStatisticsFromAssignment() {
    Cpt newStats = f.getSufficientStatisticsFromAssignment(
        f.getVars().outcomeToAssignment(assignments[1]), 2.0);
    newStats.increment(1.0);
    
    assertEquals(f.getCurrentParameters().getParents(), newStats.getParents());
    assertEquals(f.getCurrentParameters().getChildren(), newStats.getChildren());
    assertEquals(f.getCurrentParameters().getVars(), newStats.getVars());
        
    assertEquals(3.0 / 6.0, newStats.getProbability(cptVars.outcomeToAssignment(assignments[1])));
  }
  
  public void testGetSufficientStatisticsFromMarginal() {
    Cpt newStats = f.getSufficientStatisticsFromMarginal(f, 6.0, 3.0);
    
    assertEquals(f.getCurrentParameters().getParents(), newStats.getParents());
    assertEquals(f.getCurrentParameters().getChildren(), newStats.getChildren());
    assertEquals(f.getCurrentParameters().getVars(), newStats.getVars());
    
    assertEquals(factorAssignments, Sets.newHashSet(f.outcomeIterator()));
    
    newStats.increment(1.0);
    
    assertEquals(2.0 / 6.0, newStats.getProbability(cptVars.outcomeToAssignment(assignments[1])));
    assertEquals(3.0 / 6.0, newStats.getProbability(cptVars.outcomeToAssignment(assignments[2])));
  }
  
  public void testGetParameters() {
    assertEquals(cptVars, f.getCurrentParameters().getVars());    
    assertEquals(0.5, f.getCurrentParameters().getProbability(cptVars.outcomeToAssignment(assignments[0])));
  }
  
  public void testSetParameters() {
    Cpt newCpt = f.getSufficientStatisticsFromAssignment(
        f.getVars().outcomeToAssignment(assignments[0]), 1.0);
    f.setCurrentParameters(newCpt);
    assertEquals(1.0, f.getCurrentParameters().getProbability(cptVars.outcomeToAssignment(assignments[0])));
    assertEquals(0.0, f.getCurrentParameters().getProbability(cptVars.outcomeToAssignment(assignments[1])));
  }
  
  public void testGetAssignmentsWithEntry() {
    Set<Assignment> expected = Sets.newHashSet();
    expected.add(f.getVars().outcomeToAssignment(assignments[0]));
    expected.add(f.getVars().outcomeToAssignment(assignments[1]));
    expected.add(f.getVars().outcomeToAssignment(assignments[2]));

    assertEquals(expected, f.getAssignmentsWithEntry(2, Sets.<Object>newHashSet("T")));
  }
  
  public void testIteration() {
    assertEquals(factorAssignments, Sets.newHashSet(f.outcomeIterator()));
  }
}
