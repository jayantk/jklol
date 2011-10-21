package com.jayantkrish.jklol.util;

import java.util.Map;

import junit.framework.TestCase;

import com.google.common.collect.Maps;

/**
 * Unit tests for {@link CountAccumulator}.
 * 
 * @author jayantk
 */
public class CountAccumulatorTest extends TestCase {

  private CountAccumulator<String> accumulator;
  private static final double TOLERANCE = 0.000001;

  public void setUp() {
    accumulator = CountAccumulator.create();
    
    accumulator.increment("a", 0.5);
    
    Map<String, Double> countMap = Maps.newHashMap();
    countMap.put("a", 1.5);
    countMap.put("b", 0.5);
    accumulator.increment(countMap);    
  }
  
  public void testGetCount() {
    assertEquals(2.0, accumulator.getCount("a"), TOLERANCE);
    assertEquals(0.5, accumulator.getCount("b"), TOLERANCE);
    assertEquals(0.0, accumulator.getCount("c"), TOLERANCE);    
    assertEquals(2.5, accumulator.getTotalCount(), TOLERANCE);
    
    Map<String, Double> countMap = accumulator.getCountMap();
    assertEquals(2.0, countMap.get("a"), TOLERANCE);
    assertEquals(0.5, countMap.get("b"), TOLERANCE);
    assertFalse(countMap.containsKey("c"));
  }
  
  public void testGetProbability() {
    assertEquals(0.8, accumulator.getProbability("a"), TOLERANCE);
    assertEquals(0.2, accumulator.getProbability("b"), TOLERANCE);
    assertEquals(0.0, accumulator.getProbability("c"), TOLERANCE);
    
    Map<String, Double> probabilityMap = accumulator.getProbabilityMap();
    assertEquals(0.8, probabilityMap.get("a"), TOLERANCE);
    assertEquals(0.2, probabilityMap.get("b"), TOLERANCE);
    assertFalse(probabilityMap.containsKey("c"));
  }
  
  public void testIncrement() {
    CountAccumulator<String> accumulator2 = CountAccumulator.create();
    accumulator2.increment("a", 1.0);
    accumulator2.increment("c", 2.0);
    
    accumulator.increment(accumulator2);
    assertEquals(3.0, accumulator.getCount("a"), TOLERANCE);
    assertEquals(0.5, accumulator.getCount("b"), TOLERANCE);
    assertEquals(2.0, accumulator.getCount("c"), TOLERANCE);
    assertEquals(5.5, accumulator.getTotalCount(), TOLERANCE);
  }
}
