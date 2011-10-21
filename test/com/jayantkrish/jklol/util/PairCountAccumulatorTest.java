package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Collections;

import junit.framework.TestCase;

/**
 * Unit tests for {@link PairCountAccumulator}.
 * 
 * @author jayantk
 */
public class PairCountAccumulatorTest extends TestCase {

  private PairCountAccumulator<String, String> accumulator;
  
  public void setUp() {
    accumulator = PairCountAccumulator.create();

    accumulator.incrementOutcome("a", "a", 1.0);
    accumulator.incrementOutcome("a", "b", 2.0);
    accumulator.incrementOutcome("a", "a", 3.0);
    accumulator.incrementOutcome("a", "c", 1.0);
    accumulator.incrementOutcome("b", "d", 3.0);
    accumulator.incrementOutcome("b", "a", 2.0);
  }

  public void testGetCount() {
    assertEquals(4.0, accumulator.getCount("a", "a"));
    assertEquals(2.0, accumulator.getCount("a", "b"));
    assertEquals(0.0, accumulator.getCount("a", "d"));
    assertEquals(3.0, accumulator.getCount("b", "d"));
    assertEquals(2.0, accumulator.getCount("b", "a"));
    assertEquals(0.0, accumulator.getCount("b", "c"));
    assertEquals(0.0, accumulator.getCount("d", "d"));
    
    assertEquals(12.0, accumulator.getTotalCount());
    assertEquals(7.0, accumulator.getTotalCount("a"));
    assertEquals(5.0, accumulator.getTotalCount("b"));
    assertEquals(0.0, accumulator.getTotalCount("d"));
  }
  
  public void testGetProbability() {
    assertEquals(4.0 / 12.0, accumulator.getProbability("a", "a"));
    assertEquals(2.0 / 12.0, accumulator.getProbability("a", "b"));
    assertEquals(0.0 / 12.0, accumulator.getProbability("a", "d"));
    assertEquals(3.0 / 12.0, accumulator.getProbability("b", "d"));
    assertEquals(2.0 / 12.0, accumulator.getProbability("b", "a"));
    assertEquals(0.0 / 12.0, accumulator.getProbability("b", "c"));
    
    assertEquals(0.0, accumulator.getProbability("d", "d"));
  }
  
  public void testGetConditionalProbability() {
    assertEquals(4.0 / 7.0, accumulator.getConditionalProbability("a", "a"));
    assertEquals(2.0 / 7.0, accumulator.getConditionalProbability("a", "b"));
    assertEquals(0.0 / 7.0, accumulator.getConditionalProbability("a", "d"));
    assertEquals(3.0 / 5.0, accumulator.getConditionalProbability("b", "d"));
    assertEquals(2.0 / 5.0, accumulator.getConditionalProbability("b", "a"));
    assertEquals(0.0 / 5.0, accumulator.getConditionalProbability("b", "c"));
    
    assertTrue(Double.isNaN(accumulator.getConditionalProbability("d", "d")));
  }
  
  public void testGetOutcomesByProbability() {
    assertEquals(Arrays.asList("a", "b", "c"), accumulator.getOutcomesByProbability("a"));
    assertEquals(Arrays.asList("d", "a"), accumulator.getOutcomesByProbability("b"));
    assertEquals(Collections.<String>emptyList(), accumulator.getOutcomesByProbability("d"));
  }
}
