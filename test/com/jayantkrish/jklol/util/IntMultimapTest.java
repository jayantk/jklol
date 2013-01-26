package com.jayantkrish.jklol.util;

import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

public class IntMultimapTest extends TestCase {
  
  IntMultimap map;
  
  @Override
  public void setUp() {
    map = IntMultimap.create();
    
    map.put(2, 3);
    map.put(0, 5);
    map.put(7, 3);
    map.put(7, 4);
    map.put(0, 2);
  }
  
  public void testClear() {
    assertFalse(map.isEmpty());
    map.clear();
    assertFalse(map.containsKey(0));
    assertFalse(map.containsKey(7));
    assertFalse(map.containsKey(3));
    assertEquals(0, map.size());
    assertTrue(map.isEmpty());
  }
  
  public void testRetrieval() {
    assertTrue(map.containsEntry(0, 2));
    assertFalse(map.containsEntry(0, 1));
    assertTrue(map.containsEntry(0, 5));
    assertTrue(map.containsEntry(2, 3));
    assertTrue(map.containsEntry(7, 4));
    assertTrue(map.containsEntry(7, 3));
    assertFalse(map.containsEntry(7, 5));

    assertTrue(map.containsKey(0));
    assertFalse(map.containsKey(6));
    
    assertTrue(map.containsValue(5));
    assertTrue(map.containsValue(2));
    assertFalse(map.containsValue(0));
    
    Set<Integer> values = Sets.newHashSet(map.get(0));
    Set<Integer> expectedValues = Sets.newHashSet(2, 5);
    assertEquals(expectedValues, values);
    
    values = Sets.newHashSet(map.get(2));
    expectedValues = Sets.newHashSet(3);
    assertEquals(expectedValues, values);

    values = Sets.newHashSet(map.get(8));
    expectedValues = Sets.newHashSet();
    assertEquals(expectedValues, values);

    assertEquals(map.size(), 5);
    
    map.put(6, 2);
    map.putAll(9, Ints.asList(6, 7));
    
    assertEquals(map.size(), 8);

    assertTrue(map.containsEntry(6, 2));
    assertTrue(map.containsKey(6));
    assertFalse(map.containsEntry(6, 3));
    assertEquals(map.size(), 8);
    
    values = Sets.newHashSet(map.get(6));
    expectedValues = Sets.newHashSet(2);
    assertEquals(expectedValues, values);
    
    values = Sets.newHashSet(map.get(9));
    expectedValues = Sets.newHashSet(6, 7);
    assertEquals(expectedValues, values);
  }
  
  public void testInvertFrom() {
    IntMultimap map2 = IntMultimap.invertFrom(map);
    
    assertTrue(map2.containsEntry(2, 0));
    assertFalse(map2.containsEntry(2, 3));
    assertFalse(map2.containsEntry(0, 2));
    assertTrue(map2.containsEntry(3, 2));

    // Check that backing arrays were copied.
    map.put(9, 7);
    assertTrue(map.containsKey(9));
  }
}
