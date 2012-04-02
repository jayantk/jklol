package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.List;
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
    
    uniformFactor = new FunctionFactor(domain, range, function, null, null);
    
    Map<Assignment, Double> probs = Maps.newHashMap();
    probs.put(domain.outcomeArrayToAssignment("alphabet"), 2.0);
    probs.put(domain.outcomeArrayToAssignment("betabet"), 3.0);
    probs.put(domain.outcomeArrayToAssignment("bbbb"), 1.0);
    DiscreteObjectFactor domainFactor = new DiscreteObjectFactor(domain, probs);
    nonuniformFactor = new FunctionFactor(domain, range, function, domainFactor, null);
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
  
  public void testProductRange() {
    Factor rangeFactor = TableFactor.pointDistribution(range, range.outcomeArrayToAssignment("b"));
    Factor result = nonuniformFactor.product(rangeFactor);
    
    assertEquals(1.0, result.getUnnormalizedProbability("bbbb", "b"));
    assertEquals(3.0, result.getUnnormalizedProbability("betabet", "b"));
    assertEquals(0.0, result.getUnnormalizedProbability("ababa", "a"));
    assertEquals(0.0, result.getUnnormalizedProbability("ababa", "b"));
    
    Factor domainMarginal = result.marginalize(range);
    assertEquals(1.0, domainMarginal.getUnnormalizedProbability("bbbb"));
    assertEquals(3.0, domainMarginal.getUnnormalizedProbability("betabet"));
    assertEquals(0.0, domainMarginal.getUnnormalizedProbability("ababa"));
    
    // Try it with a uniform domain factor.
    result = uniformFactor.product(rangeFactor);
    assertEquals(1.0, result.getUnnormalizedProbability("bazbaz", "b"));
    assertEquals(1.0, result.getUnnormalizedProbability("bbbb", "b"));
    assertEquals(1.0, result.getUnnormalizedProbability("betabet", "b"));
    assertEquals(0.0, result.getUnnormalizedProbability("ababa", "a"));
    assertEquals(0.0, result.getUnnormalizedProbability("ababa", "b"));
    
    domainMarginal = result.marginalize(range);
    assertEquals(1.0, domainMarginal.getUnnormalizedProbability("bbbb"));
    assertEquals(1.0, domainMarginal.getUnnormalizedProbability("bazetnh"));
    assertEquals(0.0, domainMarginal.getUnnormalizedProbability("ababa"));
  }
  
  public void testMarginalRange() {
    Factor rangeMarginal = nonuniformFactor.marginalize(domain);
    
    assertEquals(4.0, rangeMarginal.getUnnormalizedProbability("b"));
    assertEquals(2.0, rangeMarginal.getUnnormalizedProbability("a"));
    assertEquals(0.0, rangeMarginal.getUnnormalizedProbability("c"));
  }
  
  public void testMaxMarginalRange() {
    Factor rangeMaxMarginal = nonuniformFactor.maxMarginalize(domain);
    assertEquals(3.0, rangeMaxMarginal.getUnnormalizedProbability("b"));
    assertEquals(2.0, rangeMaxMarginal.getUnnormalizedProbability("a"));
    assertEquals(0.0, rangeMaxMarginal.getUnnormalizedProbability("c"));
  }
  
  public void testMarginalDomain() {
    Factor rangeMarginal = nonuniformFactor.marginalize(range);
    
    assertEquals(1.0, rangeMarginal.getUnnormalizedProbability("bbbb"));
    assertEquals(3.0, rangeMarginal.getUnnormalizedProbability("betabet"));
    assertEquals(0.0, rangeMarginal.getUnnormalizedProbability("fooo"));
  }
  
  public void testProductAndMarginal() {
    Factor domainFactor = DiscreteObjectFactor.pointDistribution(domain, domain.outcomeArrayToAssignment("abcd"),
        domain.outcomeArrayToAssignment("bcde"), domain.outcomeArrayToAssignment("afff"));
    Factor rangeFactor = TableFactor.pointDistribution(range, range.outcomeArrayToAssignment("a"),
        range.outcomeArrayToAssignment("c"));
    
    Factor product = uniformFactor.product(domainFactor, rangeFactor);
    
    assertEquals(1.0, product.getUnnormalizedProbability("abcd", "a"));
    assertEquals(1.0, product.getUnnormalizedProbability("afff", "a"));
    assertEquals(0.0, product.getUnnormalizedProbability("abcd", "b"));
    assertEquals(0.0, product.getUnnormalizedProbability("bcde", "b"));
    assertEquals(0.0, product.getUnnormalizedProbability("cde", "c"));
    
    Factor rangeMarginal = product.marginalize(domain);
    assertEquals(2.0, rangeMarginal.getUnnormalizedProbability("a"));
    assertEquals(0.0, rangeMarginal.getUnnormalizedProbability("b"));
    assertEquals(0.0, rangeMarginal.getUnnormalizedProbability("c"));
    
    Factor domainMarginal = product.marginalize(range);
    assertEquals(1.0, domainMarginal.getUnnormalizedProbability("abcd"));
    assertEquals(1.0, domainMarginal.getUnnormalizedProbability("afff"));
    assertEquals(0.0, domainMarginal.getUnnormalizedProbability("bcde"));
  }
  
  public void testInverse() {
    Factor rangeFactor = TableFactor.pointDistribution(range, range.outcomeArrayToAssignment("a"),
        range.outcomeArrayToAssignment("c"));
    
    Factor result = uniformFactor.product(rangeFactor).marginalize(range);
    Factor inverse = result.inverse();
    assertEquals(1.0, inverse.getUnnormalizedProbability("abcd"));
    assertEquals(0.0, inverse.getUnnormalizedProbability("bdede"));
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

  public void testGetMostLikelyAssignments() {
    List<Assignment> mostLikely = nonuniformFactor.getMostLikelyAssignments(2);
    assertEquals(Arrays.asList("betabet", "b"), mostLikely.get(0).getValues());
    assertEquals(Arrays.asList("alphabet", "a"), mostLikely.get(1).getValues());
  }
}
