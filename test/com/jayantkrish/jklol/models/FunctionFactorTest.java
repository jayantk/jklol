package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link FunctionFactor}.
 * 
 * @author jayantk
 */
public class FunctionFactorTest extends TestCase {

  FunctionFactor uniformFactor, nonuniformFactor;
  VariableNumMap domain, range;
  
  Function<Object, Object> function;
  
  public void setUp() {
    ObjectVariable objectVar = new ObjectVariable(String.class);
    domain = new VariableNumMap(Ints.asList(1), Arrays.asList("domain"), Arrays.asList(objectVar));
    DiscreteVariable discreteVar = new DiscreteVariable("rangeVar", Arrays.asList("a", "b", "c", "d", "e"));
    range = new VariableNumMap(Ints.asList(2), Arrays.asList("range"), Arrays.asList(discreteVar));
    
    // Map each string to its first character.
    function = new Function<Object, Object>() {
      @Override
      public Object apply(Object input) {
        if (!(input instanceof String)) {
          throw new RuntimeException();
        }
        return ((String) input).substring(0, 1);
      }
    };
    
    uniformFactor = new FunctionFactor(domain, range, function, null, TableFactor.getFactory());
    
    Map<Assignment, Double> probs = Maps.newHashMap();
    probs.put(domain.outcomeArrayToAssignment("alphabet"), 2.0);
    probs.put(domain.outcomeArrayToAssignment("betabet"), 3.0);
    probs.put(domain.outcomeArrayToAssignment("bbbb"), 1.0);
    DiscreteObjectFactor domainFactor = new DiscreteObjectFactor(domain, probs);
    nonuniformFactor = new FunctionFactor(domain, range, function, domainFactor, TableFactor.getFactory());
  }
  
  public void testGetUnnormalizedProbability() {
    assertEquals(1.0, uniformFactor.getUnnormalizedProbability("alphabet", "a"));
    assertEquals(0.0, uniformFactor.getUnnormalizedProbability("alphabet", "b"));
    assertEquals(1.0, uniformFactor.getUnnormalizedProbability("bebeb", "b"));
    
    assertEquals(2.0, nonuniformFactor.getUnnormalizedProbability("alphabet", "a"));
    assertEquals(0.0, nonuniformFactor.getUnnormalizedProbability("alphabet", "b"));
    assertEquals(0.0, nonuniformFactor.getUnnormalizedProbability("bebeb", "b"));
    assertEquals(3.0, nonuniformFactor.getUnnormalizedProbability("betabet", "b"));
  }
  
  public void testConditionalDomain() {
    Factor factor = uniformFactor.conditional(domain.outcomeArrayToAssignment("bebebe"));
    assertEquals(1.0, factor.getUnnormalizedProbability("b"));
    assertEquals(0.0, factor.getUnnormalizedProbability("a"));
    assertEquals(0.0, factor.getUnnormalizedProbability("c"));
    
    factor = nonuniformFactor.conditional(domain.outcomeArrayToAssignment("bebebe"));
    assertEquals(0.0, factor.getUnnormalizedProbability("b"));
    assertEquals(0.0, factor.getUnnormalizedProbability("a"));
    assertEquals(0.0, factor.getUnnormalizedProbability("c"));
    
    factor = nonuniformFactor.conditional(domain.outcomeArrayToAssignment("betabet"));
    assertEquals(3.0, factor.getUnnormalizedProbability("b"));
    assertEquals(0.0, factor.getUnnormalizedProbability("a"));
    assertEquals(0.0, factor.getUnnormalizedProbability("c"));
  }
  
  public void testConditionalRangeNoDomain() {
    Factor factor = uniformFactor.conditional(range.outcomeArrayToAssignment("a"));
    assertEquals(1.0, factor.getUnnormalizedProbability("alphabet"));
    assertEquals(0.0, factor.getUnnormalizedProbability("betabet"));
  }
  
  public void testConditionalRange() {
    Factor factor = nonuniformFactor.conditional(range.outcomeArrayToAssignment("b"));
    assertEquals(3.0, factor.getUnnormalizedProbability("betabet"));
    assertEquals(1.0, factor.getUnnormalizedProbability("bbbb"));
    assertEquals(0.0, factor.getUnnormalizedProbability("alphabet"));
    
    factor = nonuniformFactor.conditional(range.outcomeArrayToAssignment("a"));
    assertEquals(2.0, factor.getUnnormalizedProbability("alphabet"));
    assertEquals(0.0, factor.getUnnormalizedProbability("bbbb"));
    
    factor = nonuniformFactor.conditional(range.outcomeArrayToAssignment("c"));
    assertEquals(0.0, factor.getUnnormalizedProbability("alphabet"));
    assertEquals(0.0, factor.getUnnormalizedProbability("bbbb"));
    assertEquals(0.0, factor.getUnnormalizedProbability("betabet"));
  }

}
