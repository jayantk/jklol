package com.jayantkrish.jklol.util;

import java.util.Iterator;

import junit.framework.TestCase;

public class SubsetIteratorTest extends TestCase {
  
  public void testEmpty() {
    Iterator<boolean[]> iter = new SubsetIterator(0);
    assertTrue(iter.hasNext());
    assertTrue(iter.next().length == 0);
  }
  
  public void testIter() {
    Iterator<boolean[]> iter = new SubsetIterator(3);
    
    int count = 0;
    while (iter.hasNext()) {
      boolean[] item = iter.next();
      assertEquals(3, item.length);
      count++;
    }

    assertEquals(8, count);
  }

}
