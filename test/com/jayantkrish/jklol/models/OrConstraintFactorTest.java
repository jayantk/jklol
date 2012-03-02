package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link OrConstraintFactor}.
 * 
 * @author jayantk
 */
public class OrConstraintFactorTest extends TestCase {

  OrConstraintFactor f;
  VariableNumMap inputVars, orVars;
  
  DiscreteFactor discreteFactor;
  FactorGraph factorGraph;
  
  public void setUp() {
    DiscreteVariable v = new DiscreteVariable("var", Arrays.asList("A", "B", "C", "D"));
    BooleanVariable b = new BooleanVariable();
    
    factorGraph = new FactorGraph();
    factorGraph = factorGraph.addVariable("x1", v);
    factorGraph = factorGraph.addVariable("x2", v);
    factorGraph = factorGraph.addVariable("x3", v);
    factorGraph = factorGraph.addVariable("z3", v);
    factorGraph = factorGraph.addVariable("y1", b);
    factorGraph = factorGraph.addVariable("y2", b);
    
    orVars = factorGraph.getVariables().getVariablesByName("y1", "y2");
    inputVars = factorGraph.getVariables().getVariablesByName("x1", "x2", "x3");
    Map<String, Object> varValues = Maps.newHashMap();
    varValues.put("y1", "A");
    varValues.put("y2", "C");
    f = OrConstraintFactor.createWithoutDistributions(inputVars, orVars, varValues);
    
    TableFactorBuilder dfBuilder = new TableFactorBuilder(factorGraph.getVariables().getVariablesByName("x3", "z3"));
    dfBuilder.setWeight(2.0, "A", "A");
    dfBuilder.setWeight(1.0, "A", "B");
    dfBuilder.setWeight(4.0, "B", "B");
    dfBuilder.setWeight(2.0, "B", "C");
    discreteFactor = dfBuilder.build();
  } 
  
  public void testGetUnnormalizedProbability() {
    Assignment acdAssignment = inputVars.outcomeArrayToAssignment("A", "D", "C");
    Assignment abdAssignment = inputVars.outcomeArrayToAssignment("A", "B", "D");
    Assignment bbdAssignment = inputVars.outcomeArrayToAssignment("B", "B", "D");
    
    Assignment ttAssignment = orVars.outcomeArrayToAssignment(true, true);
    assertEquals(1.0, f.getUnnormalizedProbability(ttAssignment.union(acdAssignment)));
    assertEquals(0.0, f.getUnnormalizedProbability(ttAssignment.union(abdAssignment)));

    Assignment tfAssignment = orVars.outcomeArrayToAssignment(true, false);
    assertEquals(0.0, f.getUnnormalizedProbability(tfAssignment.union(acdAssignment)));
    assertEquals(1.0, f.getUnnormalizedProbability(tfAssignment.union(abdAssignment)));
    assertEquals(0.0, f.getUnnormalizedProbability(tfAssignment.union(bbdAssignment)));
  }
  
  public void testMarginalize() {
    VariableNumMap z3 = factorGraph.getVariables().getVariablesByName("z3");
    VariableNumMap x3 = factorGraph.getVariables().getVariablesByName("x3");
    DiscreteFactor input = discreteFactor.marginalize(z3.getVariableNums());
    
    Factor predicted = f.product(input).marginalize(f.getVars().removeAll(x3));

    Iterator<Outcome> outcomeIter = input.outcomeIterator();
    while (outcomeIter.hasNext()) {
      Outcome outcome = outcomeIter.next();
      assertEquals(outcome.getProbability(), 
          predicted.getUnnormalizedProbability(outcome.getAssignment()), .00001);
    }
  }
  
  public void testConditional() {
    VariableNumMap z3 = factorGraph.getVariables().getVariablesByName("z3");
    Factor input = discreteFactor.marginalize(z3);
    Factor output = f.product(input);
    Assignment a = factorGraph.getVariables().getVariablesByName("y1", "y2")
        .outcomeArrayToAssignment(true, false);
    Factor conditional = output.conditional(a);
    assertEquals(Arrays.asList("x1", "x2", "x3"), conditional.getVars().getVariableNames());
    
    assertEquals(3.0, conditional.getUnnormalizedProbability("A", "B", "A"), .0001);
    assertEquals(6.0, conditional.getUnnormalizedProbability("A", "B", "B"), .0001);
    assertEquals(0.0, conditional.getUnnormalizedProbability("A", "C", "B"), .0001);
    assertEquals(0.0, conditional.getUnnormalizedProbability("B", "C", "B"), .0001);
    assertEquals(3.0, conditional.getUnnormalizedProbability("B", "B", "A"), .0001);
    assertEquals(0.0, conditional.getUnnormalizedProbability("B", "B", "B"), .0001);
    
    VariableNumMap x3 = factorGraph.getVariables().getVariablesByName("x3");
    Factor maxMarginal = conditional.maxMarginalize(conditional.getVars().removeAll(x3));
    assertEquals(0.0, maxMarginal.getUnnormalizedProbability("B"), .0001);
    assertEquals(0.0, maxMarginal.getUnnormalizedProbability("C"), .0001);
    assertEquals(3.0, maxMarginal.getUnnormalizedProbability("A"), .0001);
    
    VariableNumMap x2 = factorGraph.getVariables().getVariablesByName("x2");
    maxMarginal = conditional.maxMarginalize(conditional.getVars().removeAll(x2));
    assertEquals(1.0, maxMarginal.getUnnormalizedProbability("B"), .0001);
    assertEquals(0.0, maxMarginal.getUnnormalizedProbability("C"), .0001);
    assertEquals(1.0, maxMarginal.getUnnormalizedProbability("A"), .0001);
    
    Assignment xAssignment = factorGraph.getVariables().getVariablesByName("x1", "x2") 
        .outcomeArrayToAssignment("B", "B");
    Factor conditional2 = conditional.conditional(xAssignment);
    assertEquals(0.0, conditional2.getUnnormalizedProbability("B"), .0001);
    assertEquals(0.0, conditional2.getUnnormalizedProbability("C"), .0001);
    assertEquals(3.0, conditional2.getUnnormalizedProbability("A"), .0001);
    
    xAssignment = factorGraph.getVariables().getVariablesByName("x1", "x2")
        .outcomeArrayToAssignment("B", "C");
    conditional2 = conditional.conditional(xAssignment);
    assertEquals(0.0, conditional2.getUnnormalizedProbability("B"), .0001);
    assertEquals(0.0, conditional2.getUnnormalizedProbability("C"), .0001);
    assertEquals(0.0, conditional2.getUnnormalizedProbability("A"), .0001);
    
    xAssignment = factorGraph.getVariables().getVariablesByName("x1", "x2")
        .outcomeArrayToAssignment("A", "B");
    conditional2 = conditional.conditional(xAssignment);
    assertEquals(6.0, conditional2.getUnnormalizedProbability("B"), .0001);
    assertEquals(0.0, conditional2.getUnnormalizedProbability("C"), .0001);
    assertEquals(3.0, conditional2.getUnnormalizedProbability("A"), .0001);
    
    assertEquals(conditional2.getVars().outcomeArrayToAssignment("B"),
        conditional2.getMostLikelyAssignments(1).get(0)); 
  }
  
  public void testConditional2() {
    VariableNumMap z3 = factorGraph.getVariables().getVariablesByName("z3");
    VariableNumMap x3 = factorGraph.getVariables().getVariablesByName("x3");
    Factor input = discreteFactor.marginalize(z3);
    Factor output = f.product(input);
    Assignment a = factorGraph.getVariables().getVariablesByName("y1", "y2")
        .outcomeArrayToAssignment(true, true);
    Factor conditional = output.conditional(a);
    assertEquals(Arrays.asList("x1", "x2", "x3"), conditional.getVars().getVariableNames());
    
    Factor maxMarginal = conditional.maxMarginalize(conditional.getVars().removeAll(x3));
    assertEquals(0.0, maxMarginal.getUnnormalizedProbability("B"), .0001);
    assertEquals(0.0, maxMarginal.getUnnormalizedProbability("C"), .0001);
    assertEquals(3.0, maxMarginal.getUnnormalizedProbability("A"), .0001);

    Assignment best = conditional.getMostLikelyAssignments(1).get(0);
    assertTrue(best.getValues().contains("C"));
    assertTrue(best.getValues().contains("A"));
  }
  
  public void testGetMostLikelyAssignments() {
    VariableNumMap z3 = factorGraph.getVariables().getVariablesByName("z3");
    VariableNumMap x2 = factorGraph.getVariables().getVariablesByName("x2");
    VariableNumMap x1 = factorGraph.getVariables().getVariablesByName("x1");
    Factor input = discreteFactor.marginalize(z3);
    Factor output = f.product(Arrays.asList(input, 
        TableFactor.pointDistribution(x2, x2.outcomeArrayToAssignment("A")),
        TableFactor.pointDistribution(x1, x1.outcomeArrayToAssignment("A"))));
    
    Assignment best = output.getMostLikelyAssignments(1).get(0);
    assertEquals(f.getVars().outcomeArrayToAssignment("A", "A", "B", true, false), best);
  }
}
