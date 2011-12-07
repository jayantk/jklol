package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Iterator;

import junit.framework.TestCase;

/**
 * Unit tests for {@link IntegerArrayIterator}.
 * 
 * @author jayantk
 */
public class IntegerArrayIteratorTest extends TestCase {

  Iterator<int[]> iter;
  Iterator<int[]> zeroVarIter;
  
  public void setUp() {
    iter = new IntegerArrayIterator(new int[] {1, 3, 2});
    zeroVarIter = new IntegerArrayIterator(new int[] {});
  }
  
  public void testIterator() {
    int count = 0;
    while (iter.hasNext()) {
      assertEquals(0, iter.next()[0]);
      count++;
    }
    assertEquals(6, count);
  }
  
  public void testZeroVarIterator() {
    assertTrue(zeroVarIter.hasNext());
    assertTrue(Arrays.equals(new int[] {}, zeroVarIter.next()));
    assertFalse(zeroVarIter.hasNext());
  }
}
