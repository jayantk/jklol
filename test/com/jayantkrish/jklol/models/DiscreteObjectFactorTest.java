package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link DiscreteObjectFactor}.
 * 
 * @author jayant
 */
public class DiscreteObjectFactorTest extends TestCase {

  private Variable var0, var1;
  private VariableNumMap fVars, gVars;

  private DiscreteObjectFactor f;
  private DiscreteObjectFactor g;
  
  private Collection<Assignment> gAssignments;
  
  public void setUp() {
    var0 = new ObjectVariable(Integer.class);
    var1 = new ObjectVariable(String.class);
    
    fVars = new VariableNumMap(Arrays.asList(0, 1), Arrays.asList("0", "1"),
        Arrays.asList(var0, var1));
    
    Map<Assignment, Double> fMap = Maps.newHashMap();
    fMap.put(fVars.outcomeArrayToAssignment(0, "0"), 1.0);
    fMap.put(fVars.outcomeArrayToAssignment(0, "1"), 2.0);
    fMap.put(fVars.outcomeArrayToAssignment(1, "0"), 3.0);
    fMap.put(fVars.outcomeArrayToAssignment(2, "2"), 4.0);
    f = new DiscreteObjectFactor(fVars, fMap);

    gVars = new VariableNumMap(Arrays.asList(0), Arrays.asList("0"),
        Arrays.asList(var0));
    
    Map<Assignment, Double> gMap = Maps.newHashMap();
    gMap.put(gVars.outcomeArrayToAssignment(0), 5.0);
    gMap.put(gVars.outcomeArrayToAssignment(1), 6.0);
    gAssignments = gMap.keySet();
    g = new DiscreteObjectFactor(gVars, gMap);
  }
  
  public void testAssignments() {
    Set<Assignment> actualGAssignments = Sets.newHashSet(g.assignments());
    assertTrue(actualGAssignments.containsAll(gAssignments));
    assertTrue(gAssignments.containsAll(actualGAssignments));
  }
  
  public void testGetUnnormalizedProbability() {
    assertEquals(5.0, g.getUnnormalizedProbability(0));
    assertEquals(0.0, g.getUnnormalizedProbability(7));
  }
  
  public void testConditional() {
    Factor conditional = f.conditional(gVars.outcomeArrayToAssignment(0));
    assertEquals(1.0, conditional.getUnnormalizedProbability("0"));
    assertEquals(0.0, conditional.getUnnormalizedProbability(0));
    assertEquals(2.0, conditional.getUnnormalizedProbability("1"));
    assertEquals(0.0, conditional.getUnnormalizedProbability("2"));
  }
  
  public void testMarginalize() {
    Factor marginal = f.marginalize(0);
    assertEquals(4.0, marginal.getUnnormalizedProbability("0"));
    assertEquals(0.0, marginal.getUnnormalizedProbability(0));
    assertEquals(2.0, marginal.getUnnormalizedProbability("1"));
    assertEquals(4.0, marginal.getUnnormalizedProbability("2"));
    assertEquals(0.0, marginal.getUnnormalizedProbability("3"));
  }
  
  public void testMaxMarginalize() {
    Factor marginal = f.maxMarginalize(0);
    assertEquals(3.0, marginal.getUnnormalizedProbability("0"));
    assertEquals(0.0, marginal.getUnnormalizedProbability(0));
    assertEquals(2.0, marginal.getUnnormalizedProbability("1"));
    assertEquals(4.0, marginal.getUnnormalizedProbability("2"));
    assertEquals(0.0, marginal.getUnnormalizedProbability("3"));    
  }
  
  public void testProduct() {
    Factor product = f.product(g);
    assertEquals(5.0, product.getUnnormalizedProbability(0, "0"));
    assertEquals(0.0, product.getUnnormalizedProbability(0, "2"));
    assertEquals(10.0, product.getUnnormalizedProbability(0, "1"));
    assertEquals(18.0, product.getUnnormalizedProbability(1, "0"));
    assertEquals(0.0, product.getUnnormalizedProbability(2, "2"));
  }
  
  public void testRelabelVariables() {
    BiMap<Integer, Integer> integerRelabeling = HashBiMap.create();
    integerRelabeling.put(0, 1);
    integerRelabeling.put(1, 2);

    BiMap<String, String> stringRelabeling = HashBiMap.create();
    stringRelabeling.put("0", "x");
    stringRelabeling.put("1", "y");
    
    VariableRelabeling relabeling = new VariableRelabeling(integerRelabeling, stringRelabeling);
    DiscreteObjectFactor relabeled = f.relabelVariables(relabeling);
    
    assertEquals(new VariableNumMap(Arrays.asList(1, 2), Arrays.asList("x", "y"), Arrays.asList(var0, var1)),
        relabeled.getVars());
    assertEquals(4, Iterables.size(relabeled.assignments()));
  }
}
