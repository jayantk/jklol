package com.jayantkrish.jklol.models.bayesnet;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class SparseCptTableFactorTest extends TestCase {

  SparseCptTableFactor f;  
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
    
    f = new SparseCptTableFactor(parents, children, TableFactor.unity(allVars),
        TableFactor.zero(allVars));
    
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
      f.incrementSufficientStatisticsFromAssignment(parameters, parameters, assignment, 1.0);
      factorAssignments.add(assignment);
    }
  }
  
  public void testGetNewSufficientStatistics() {
    DiscreteFactor factor = f.getModelFromParameters(f.getNewSufficientStatistics());
    assertEquals(allVars, factor.getVars());
    
    // All assignments should have a count of 0.
    Iterator<Outcome> iter = factor.outcomeIterator();
    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      assertEquals(0.0, factor.getUnnormalizedProbability(outcome.getAssignment()));
      assertEquals(0.0, outcome.getProbability());
    }
  }
  
  public void testGetSufficientStatisticsFromAssignment() {
    SufficientStatistics newStats = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromAssignment(newStats, newStats,
        f.getVars().outcomeToAssignment(assignments[1]), 2.0);
    newStats.increment(1.0);

    DiscreteFactor factor = f.getModelFromParameters(newStats);
    assertEquals(3.0 / 6.0, factor.getUnnormalizedProbability(allVars.outcomeToAssignment(assignments[1])));
  }
  
  public void testGetSufficientStatisticsFromMarginal() {
    SufficientStatistics newStats = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromMarginal(newStats, newStats,
        f.getModelFromParameters(parameters), Assignment.EMPTY, 6.0, 3.0);
    newStats.increment(1.0);
    
    DiscreteFactor factor = f.getModelFromParameters(newStats);
    assertEquals(2.0 / 6.0, factor.getUnnormalizedProbability(allVars.outcomeToAssignment(assignments[1])));
    assertEquals(3.0 / 6.0, factor.getUnnormalizedProbability(allVars.outcomeToAssignment(assignments[2])));
  }
    
  public void testGetFactorFromParameters() {
    DiscreteFactor factor = f.getModelFromParameters(parameters);
    Iterator<Outcome> iter = factor.outcomeIterator();
    Set<Assignment> foundOutcomes = Sets.newHashSet();
    while (iter.hasNext()) {
      foundOutcomes.add(iter.next().getAssignment());
    }

    assertEquals(0.5, factor.getUnnormalizedProbability(
        f.getVars().outcomeToAssignment(assignments[0])));
    assertEquals(0.0, factor.getUnnormalizedProbability(
        f.getVars().outcomeToAssignment(new Object[] { "T", "T", "F", "F" })));
    assertEquals(1.0, factor.getUnnormalizedProbability(
        f.getVars().outcomeToAssignment(assignments[2])));
  }
}
