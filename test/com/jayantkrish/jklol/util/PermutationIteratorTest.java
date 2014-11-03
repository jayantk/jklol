package com.jayantkrish.jklol.util;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

/**
 * Unit tests for {@link PermutationIterator}.
 *  
 * @author jayantk
 */
public class PermutationIteratorTest extends TestCase {

  public void testEmpty() {
    PermutationIterator empty = new PermutationIterator(0);
    assertTrue(empty.hasNext());
    assertTrue(empty.next().length == 0);
    
    assertFalse(empty.hasNext());
    try {
      empty.next();
    } catch (NoSuchElementException e) {
      return;
    }
    fail("Expected NoSuchElementException");
  }
  
  public void testIterator() {
    PermutationIterator iter = new PermutationIterator(3);
    
    Set<List<Integer>> permutations = Sets.newHashSet();
    while (iter.hasNext()) {
      int[] next = iter.next();
      permutations.add(Ints.asList(next));
    }
    
    assertEquals(6, permutations.size());
    
    // This is a hacky way to generate all permutations of length 3.
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        if (j == i) { continue; }
        for (int k = 0; k < 3; k++) {
          if (k == j || k == i) { continue; }
          assertTrue(permutations.contains(Ints.asList(i, j, k)));
        } 
      } 
    }
  }
}
