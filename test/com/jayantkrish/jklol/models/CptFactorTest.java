package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class CptFactorTest extends TestCase {

  CptTableFactor f;  
  SufficientStatistics parameters;
  VariableNumMap parents, children, allVars;
  
  private Object[][] assignments;
  Set<Assignment> factorAssignments;
  
  public void setUp() {
    DiscreteVariable v = new DiscreteVariable("Two values",
        Arrays.asList("T", "F" ));
    
    parents = new VariableNumMap(Arrays.asList(2, 3),
        Arrays.asList("v2", "v3"),
        Arrays.asList(v, v ));
    children = new VariableNumMap(Arrays.asList(4, 5),
        Arrays.asList("v4", "v5"),
        Arrays.asList(v, v));
    allVars = parents.union(children);
    
    f = new CptTableFactor(parents, children);
    
    // Note: Parent F, T was unassigned!
    assignments = new Object[][] {{ "T", "T", "T", "T" },
        { "T", "T", "F", "T" },
        { "T", "F", "F", "T" },
        { "F", "F", "F", "F" },
        { "F", "F", "T", "T" }};
    factorAssignments = Sets.newHashSet();
    parameters = f.getNewSufficientStatistics();
    for (int i = 0; i < assignments.length; i++) {
      Assignment assignment = f.getVars().outcomeToAssignment(assignments[i]);
      f.incrementSufficientStatisticsFromAssignment(parameters, assignment, 1.0);
      factorAssignments.add(assignment);
    }
  }
  
  public void testGetNewSufficientStatistics() {
    Cpt newStats = f.getNewSufficientStatistics();
    
    assertEquals(parents, newStats.getParents());
    assertEquals(children, newStats.getChildren());
    assertEquals(allVars, newStats.getVars());
    
    // All assignments should have a count of 0.
    assertFalse(newStats.assignmentIterator().hasNext());
  }
  
  public void testGetSufficientStatisticsFromAssignment() {
    Cpt newStats = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromAssignment(newStats,
        f.getVars().outcomeToAssignment(assignments[1]), 2.0);
    newStats.increment(1.0);

    assertEquals(parents, newStats.getParents());
    assertEquals(children, newStats.getChildren());
    assertEquals(allVars, newStats.getVars());

    assertEquals(3.0 / 6.0, newStats.getProbability(allVars.outcomeToAssignment(assignments[1])));
  }
  
  public void testGetSufficientStatisticsFromMarginal() {
    Cpt newStats = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromMarginal(newStats, 
        f.getFactorFromParameters(parameters), Assignment.EMPTY, 6.0, 3.0);

    assertEquals(parents, newStats.getParents());
    assertEquals(children, newStats.getChildren());
    assertEquals(allVars, newStats.getVars());
    
    newStats.increment(1.0);
    
    assertEquals(2.0 / 6.0, newStats.getProbability(allVars.outcomeToAssignment(assignments[1])));
    assertEquals(3.0 / 6.0, newStats.getProbability(allVars.outcomeToAssignment(assignments[2])));
  }
    
  public void testGetFactorFromParameters() {
    DiscreteFactor factor = f.getFactorFromParameters(parameters);
    assertEquals(factorAssignments, Sets.newHashSet(factor.outcomeIterator()));
    
    assertEquals(0.5, factor.getUnnormalizedProbability(
        f.getVars().outcomeToAssignment(assignments[0])));
    assertEquals(0.0, factor.getUnnormalizedProbability(
        f.getVars().outcomeToAssignment(new Object[] { "T", "T", "F", "F" })));
    assertEquals(1.0, factor.getUnnormalizedProbability(
        f.getVars().outcomeToAssignment(assignments[2])));
  }
}
