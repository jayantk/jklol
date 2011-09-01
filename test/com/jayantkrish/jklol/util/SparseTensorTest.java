package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

public class SparseTensorTest extends TestCase {

  private int[] varNums;
  private SparseTensor smallTable, table, missingMiddle, missingFirst,
      missingLast, emptyTable, addTable;

  private int[] a1, a2;

  @Override
  public void setUp() {
    varNums = new int[] { 1, 3, 4 };

    a1 = new int[] { 0, 0, 0 };
    a2 = new int[] { 0, 2, 3 };

    SparseTensorBuilder builder = new SparseTensorBuilder(varNums);
    builder.put(a1, 1.0);
    builder.put(a2, 2.0);
    smallTable = builder.build();
    builder.put(new int[] {4, 0, 0}, 3.0);
    builder.put(new int[] {3, 1, 0}, 3.0);
    builder.put(new int[] {3, 0, 1}, 6.0);
    addTable = builder.build();
    builder.put(new int[] {4, 0, 0}, 0.0);
    builder.put(new int[] { 0, 0, 3 }, 3.0);
    builder.put(new int[] { 0, 1, 3 }, 4.0);
    builder.put(new int[] { 1, 0, 3 }, 5.0);
    builder.put(new int[] { 0, 3, 0 }, 3.0);
    builder.put(new int[] { 0, 3, 1 }, 4.0);
    builder.put(new int[] { 1, 3, 0 }, 5.0);
    builder.put(new int[] { 3, 0, 0 }, 3.0);
    builder.put(new int[] { 3, 0, 1 }, 4.0);
    builder.put(new int[] { 3, 1, 0 }, 5.0);
    builder.put(new int[] { 2, 0, 0 }, 3.0);
    builder.put(new int[] { 2, 0, 1 }, 4.0);
    builder.put(new int[] { 2, 1, 0 }, 5.0);
    builder.put(new int[] { 4, 0, 1 }, 5.0);
    builder.put(new int[] { 4, 1, 0 }, 5.0);
    table = builder.build();

    builder = new SparseTensorBuilder(new int[] { 3, 4 });
    builder.put(new int[] { 0, 3 }, 2.0);
    builder.put(new int[] { 1, 3 }, 3.0);
    builder.put(new int[] { 3, 0 }, 4.0);
    missingFirst = builder.build();

    builder = new SparseTensorBuilder(new int[] { 1, 4 });
    builder.put(new int[] { 0, 3 }, 2.0);
    builder.put(new int[] { 1, 3 }, 3.0);
    builder.put(new int[] { 3, 0 }, 4.0);
    builder.put(new int[] { 4, 0 }, 5.0);
    builder.put(new int[] { 4, 1 }, 6.0);
    missingMiddle = builder.build();

    builder = new SparseTensorBuilder(new int[] { 1, 3 });
    builder.put(new int[] { 0, 3 }, 2.0);
    builder.put(new int[] { 1, 3 }, 3.0);
    builder.put(new int[] { 3, 0 }, 4.0);
    builder.put(new int[] { 4, 0 }, 5.0);
    builder.put(new int[] { 4, 1 }, 6.0);
    missingLast = builder.build();

    // Empty table is a table with no dimensions, which should behave like a
    // scalar.
    builder = new SparseTensorBuilder(new int[] {});
    builder.put(new int[] {}, 5.0);
    emptyTable = builder.build();
  }

  public void testGetVarNums() {
    assertTrue(Arrays.equals(varNums, table.getDimensionNumbers()));
    assertTrue(Arrays.equals(new int[] {}, emptyTable.getDimensionNumbers()));
  }

  public void testGet() {
    assertEquals(1.0, table.get(a1));
    assertEquals(2.0, table.get(a2));
    assertEquals(2.0, table.get(Arrays.copyOf(a2, a2.length)));

    assertEquals(0.0, table.get(new int[] { 0, 0, 1 }));
    assertEquals(0.0, table.get(new int[] { 0, 1, 0 }));
    assertEquals(0.0, table.get(new int[] { 1, 0, 0 }));

    assertEquals(5.0, emptyTable.get(new int[] {}));
    try {
      table.get(new int[] { 0, 0 });
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException.");
  }

  public void testContainsKey() {
    assertEquals(true, table.containsKey(a1));
    assertEquals(true, table.containsKey(a2));
    assertEquals(true, table.containsKey(Arrays.copyOf(a2, a2.length)));

    assertEquals(false, table.containsKey(new int[] { 0, 0, 1 }));
    assertEquals(false, table.containsKey(new int[] { 0, 1, 0 }));
    assertEquals(false, table.containsKey(new int[] { 1, 0, 0 }));

    assertEquals(true, emptyTable.containsKey(new int[] {}));
    try {
      table.containsKey(new int[] { 0, 0 });
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException.");
  }

  public void testSize() {
    assertEquals(2, smallTable.size());
    assertEquals(1, emptyTable.size());
  }

  public void testAssignmentIterator() {
    Set<List<Integer>> expectedKeys = Sets.newHashSet();
    expectedKeys.add(Ints.asList(a1));
    expectedKeys.add(Ints.asList(a2));
    Set<List<Integer>> actualKeys = Sets.newHashSet();
    for (int[] key : Lists.newArrayList(smallTable.keyIterator())) {
      actualKeys.add(Ints.asList(key));
    }
    assertEquals(expectedKeys, actualKeys);

    expectedKeys = Sets.newHashSet();
    expectedKeys.add(Lists.<Integer> newArrayList());
    actualKeys.clear();
    for (int[] key : Lists.newArrayList(emptyTable.keyIterator())) {
      actualKeys.add(Ints.asList(key));
    }
    assertEquals(expectedKeys, actualKeys);
  }

  public void testElementwiseProductEmpty() {
    SparseTensor expected = simpleMultiply(table, emptyTable);
    SparseTensor actual = table.elementwiseProduct(emptyTable);

    assertEquals(expected, actual);
  }
  
  public void testElementwiseProductEmpty2() {
    SparseTensor expected = simpleMultiply(emptyTable, emptyTable);
    SparseTensor actual = emptyTable.elementwiseProduct(emptyTable);

    assertEquals(expected, actual);
  }

  public void testElementwiseProductMissingFirst() {
    SparseTensor expected = simpleMultiply(table, missingFirst);
    SparseTensor actual = table.elementwiseProduct(missingFirst);

    assertEquals(expected, actual);
  }

  public void testElementwiseProductMissingMiddle() {
    SparseTensor expected = simpleMultiply(table, missingMiddle);
    SparseTensor actual = table.elementwiseProduct(missingMiddle);

    assertEquals(expected, actual);
  }

  public void testElementwiseProductMissingLast() {
    SparseTensor expected = simpleMultiply(table, missingLast);
    SparseTensor actual = table.elementwiseProduct(missingLast);

    assertEquals(expected, actual);
  }

  public void testElementwiseAddition() {
    SparseTensor actual = table.elementwiseAddition(addTable);

    assertEquals(2.0, actual.get(a1));
    assertEquals(4.0, actual.get(a2));
    assertEquals(3.0, actual.get(new int[] {4, 0, 0}));
    assertEquals(5.0, actual.get(new int[] {1, 0, 3}));
    assertEquals(0.0, actual.get(new int[] {5, 1, 0}));
  }
  
  public void testElementwiseMaximum() {
    SparseTensor actual = table.elementwiseMaximum(addTable);
    
    assertEquals(1.0, actual.get(a1));
    assertEquals(2.0, actual.get(a2));
    assertEquals(6.0, actual.get(new int[] {3, 0, 1}));
    assertEquals(5.0, actual.get(new int[] {3, 1, 0}));
    assertEquals(0.0, actual.get(new int[] {5, 1, 0}));
  }

  public void testElementwiseInverse() {
    SparseTensor actual = table.elementwiseInverse();
    
    assertEquals(1.0, actual.get(a1));
    assertEquals(1.0 / 2.0, actual.get(a2));
    assertEquals(0.0, actual.get(new int[] {5, 1, 0}));
  }
  
  public void testReduceDimensionsNone() {
    runReduceTest(table, Sets.<Integer>newHashSet());
  }
  
  public void testReduceFirstDimension() {
    runReduceTest(table, Sets.<Integer>newHashSet(1));
  }
  
  public void testReduceSecondDimension() {
    runReduceTest(table, Sets.<Integer>newHashSet(3));
  }
  
  public void testReduceThirdDimension() {
    runReduceTest(table, Sets.<Integer>newHashSet(4));
  }
  
  public void testReduceTwoDimensions() {
    runReduceTest(table, Sets.<Integer>newHashSet(1, 4));
  }
    
  public void testReduceTwoDimensions2() {
    runReduceTest(table, Sets.<Integer>newHashSet(3, 4));
  }
  
  public void testReduceAllDimensions() {
    runReduceTest(table, Sets.<Integer>newHashSet(1, 3, 4));
  }
  
  public void testRelabelDimensionsSameOrder() {
    SparseTensor actual = table.relabelDimensions(new int[] {5, 6, 7});
    assertTrue(Arrays.equals(new int[] {5, 6, 7}, actual.getDimensionNumbers()));
    
    Function<int[], List<Integer>> toList = new Function<int[], List<Integer>>() {
      @Override
      public List<Integer> apply(int[] key) {
        return Ints.asList(key);
      }
    };
    
    assertEquals(Lists.newArrayList(Iterators.transform(table.keyIterator(), toList)),
          Lists.newArrayList(Iterators.transform(actual.keyIterator(), toList)));
  }
  
  public void testRelabelDimensions() {
    SparseTensor actual = table.relabelDimensions(new int[] {7, 5, 6});
    
    assertTrue(Arrays.equals(new int[] {5, 6, 7}, actual.getDimensionNumbers()));
    assertEquals(table.size(), actual.size());
    int[] prevKey = null;
    Iterator<int[]> keyIter = actual.keyIterator();
    while (keyIter.hasNext()) {
      int[] key = keyIter.next();
      int[] oldKey = new int[key.length];
      oldKey[0] = key[2];
      oldKey[2] = key[1];
      oldKey[1] = key[0];
      assertEquals(table.get(oldKey), actual.get(key));
      if (prevKey != null) {
        assertTrue(Ints.lexicographicalComparator().compare(prevKey, key) < 0);
      }
      prevKey = key;
    }
  }
  
  /**
   * This is a simple version of the elementwise multiply algorithm which looks
   * at all pairs of keys in {@code first} and {@code second}. {@code second}
   * must contain a subset of the dimensions of {@code first}.
   * 
   * @param first
   * @param second
   * @return
   */
  private SparseTensor simpleMultiply(SparseTensor first, SparseTensor second) {
    SparseTensorBuilder builder = new SparseTensorBuilder(first.getDimensionNumbers());

    int firstInd = 0;
    int[] alignment = new int[second.getDimensionNumbers().length];
    for (int i = 0; i < second.getDimensionNumbers().length; i++) {
      while (first.getDimensionNumbers()[firstInd] < second.getDimensionNumbers()[i]) {
        firstInd++;
      }
      Preconditions.checkArgument(first.getDimensionNumbers()[firstInd] == second.getDimensionNumbers()[i]);
      alignment[i] = firstInd;
    }

    Iterator<int[]> firstIter = first.keyIterator();
    while (firstIter.hasNext()) {
      int[] firstKey = firstIter.next();
      Iterator<int[]> secondIter = second.keyIterator();
      while (secondIter.hasNext()) {
        int[] secondKey = secondIter.next();
        boolean equal = true;
        for (int i = 0; i < alignment.length; i++) {
          equal = equal && secondKey[i] == firstKey[alignment[i]];
        }

        if (equal) {
          builder.put(firstKey, first.get(firstKey) * second.get(secondKey));
        }
      }
    }
    return builder.build();
  }

  /**
   * Helper method for testing sumOutDimensions / maxOutDimensions.
   */
  private void runReduceTest(SparseTensor table, Set<Integer> dimsToEliminate) {
    SparseTensor expected = simpleReduce(table, dimsToEliminate, true);
    SparseTensor actual = table.sumOutDimensions(dimsToEliminate);
    assertEquals(expected, actual);
    expected = simpleReduce(table, dimsToEliminate, false);
    actual = table.maxOutDimensions(dimsToEliminate);
    assertEquals(expected, actual);
  }
  
  /**
   * This is a simple version of sum/max out dimensions algorithm. 
   */
  private SparseTensor simpleReduce(SparseTensor first, Set<Integer> dimsToEliminate,
      boolean useSum) {
    int[] currentDims = first.getDimensionNumbers();
    int[] newDimNums = new int[currentDims.length];
    Map<Integer, Integer> newDimIndices = Maps.newHashMap();    
    for (int i = 0; i < currentDims.length; i++) {
      if (!dimsToEliminate.contains(currentDims[i])) {
        newDimNums[newDimIndices.size()] = currentDims[i];
        newDimIndices.put(i, newDimIndices.size());
      }
    }
    
    SparseTensorBuilder builder = new SparseTensorBuilder(
        Arrays.copyOf(newDimNums, newDimIndices.size()));
    Iterator<int[]> keyIter = first.keyIterator();
    while (keyIter.hasNext()) {
      int[] curKey = keyIter.next();
      int[] newKey = new int[newDimIndices.size()];
      for (Map.Entry<Integer, Integer> entry : newDimIndices.entrySet()) {
        newKey[entry.getValue()] = curKey[entry.getKey()];
      }
      
      if (useSum) {
        builder.put(newKey, builder.get(newKey) + first.get(curKey));
      } else {
        builder.put(newKey, Math.max(builder.get(newKey), first.get(curKey)));
      }
    }
    return builder.build();
  }
}
