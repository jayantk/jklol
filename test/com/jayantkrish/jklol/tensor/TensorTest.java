package com.jayantkrish.jklol.tensor;

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
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.tensor.TensorProtos.TensorProto;

/**
 * Implementation-independent test cases for tensor operations.
 * 
 * @author jayantk
 */
public abstract class TensorTest extends TestCase {

  private final TensorFactory tensorFactory;
  private final List<TensorFactory> allTensorFactories;
  
  protected int[] varNums, varSizes;
  protected Tensor table, emptyInputTable;
  
  protected List<Tensor> smallTables, missingMiddles, missingFirsts,
      missingLasts, emptyTables, addTables;

  protected int[] a1, a2;
  
  public TensorTest(TensorFactory tensorFactory) {
    this.tensorFactory = tensorFactory;
    this.allTensorFactories = Lists.newArrayList(SparseTensorBuilder.getFactory(), 
        DenseTensorBuilder.getFactory());
  }

  @Override
  public void setUp() {
    varNums = new int[] { 1, 3, 4 };
    varSizes = new int[] {6, 5, 4};

    a1 = new int[] { 0, 0, 0 };
    a2 = new int[] { 0, 2, 3 };

    // Build the two tensors which are determined by tensorFactory.
    TensorBuilder builder = tensorFactory.getBuilder(varNums, varSizes);
    builder.put(a1, 1.0);
    builder.put(a2, 2.0);
    // These keys are overwritten later.
    builder.put(new int[] {4, 0, 0}, 3.0);
    builder.put(new int[] {3, 1, 0}, 3.0);
    builder.put(new int[] {3, 0, 1}, 6.0);
    // None of the following keys are overwritten.
    builder.put(new int[] { 0, 0, 3 }, 3.0);
    builder.put(new int[] { 0, 1, 3 }, 4.0);
    builder.put(new int[] { 1, 0, 3 }, 5.0);
    builder.put(new int[] { 0, 3, 0 }, 3.0);
    builder.put(new int[] { 0, 3, 1 }, 4.0);
    builder.put(new int[] { 1, 3, 0 }, 5.0);
    builder.put(new int[] { 3, 0, 0 }, 3.0);
    builder.put(new int[] { 3, 0, 1 }, 4.0);
    builder.put(new int[] { 3, 1, 0 }, 5.0);
    builder.put(new int[] { 3, 4, 3 }, 7.0);
    builder.put(new int[] { 2, 0, 0 }, 3.0);
    builder.put(new int[] { 2, 0, 1 }, 4.0);
    builder.put(new int[] { 2, 1, 0 }, 5.0);
    builder.put(new int[] { 4, 0, 0 }, 0.0);
    builder.put(new int[] { 4, 0, 1 }, 5.0);
    builder.put(new int[] { 4, 1, 0 }, 5.0);
    table = builder.build();
    
    // Empty table is a table with no dimensions, which should behave like a
    // scalar.
    builder = tensorFactory.getBuilder(new int[] {}, new int[] {});
    builder.put(new int[] {}, 5.0);
    emptyInputTable = builder.build();

    smallTables = Lists.newArrayList();
    addTables = Lists.newArrayList();
    missingFirsts = Lists.newArrayList();
    missingMiddles = Lists.newArrayList();
    missingLasts = Lists.newArrayList();
    emptyTables = Lists.newArrayList();
    for (TensorFactory otherFactory : allTensorFactories) {
      builder = otherFactory.getBuilder(varNums, varSizes);
      builder.put(a1, 1.0);
      builder.put(a2, 2.0);
      smallTables.add(builder.build());
      builder.put(new int[] {4, 0, 0}, 3.0);
      builder.put(new int[] {3, 1, 0}, 3.0);
      builder.put(new int[] {3, 0, 1}, 6.0);
      addTables.add(builder.build());

      builder = otherFactory.getBuilder(new int[] { 3, 4 }, new int[] {5, 4});
      builder.put(new int[] { 0, 3 }, 2.0);
      builder.put(new int[] { 1, 3 }, 3.0);
      builder.put(new int[] { 3, 0 }, 4.0);
      missingFirsts.add(builder.build());

      builder = otherFactory.getBuilder(new int[] { 1, 4 }, new int[] {6, 4});
      builder.put(new int[] { 0, 3 }, 2.0);
      builder.put(new int[] { 1, 3 }, 3.0);
      builder.put(new int[] { 3, 0 }, 4.0);
      builder.put(new int[] { 4, 0 }, 5.0);
      builder.put(new int[] { 4, 1 }, 6.0);
      missingMiddles.add(builder.build());

      builder = otherFactory.getBuilder(new int[] { 1, 3 }, new int[] {6, 5});
      builder.put(new int[] { 0, 3 }, 2.0);
      builder.put(new int[] { 1, 3 }, 3.0);
      builder.put(new int[] { 3, 0 }, 4.0);
      builder.put(new int[] { 4, 0 }, 5.0);
      builder.put(new int[] { 4, 1 }, 6.0);
      missingLasts.add(builder.build());

      // Empty table is a table with no dimensions, which should behave like a
      // scalar.
      builder = otherFactory.getBuilder(new int[] {}, new int[] {});
      builder.put(new int[] {}, 5.0);
      emptyTables.add(builder.build());
    }
  }

  public void testGetVarNums() {
    assertTrue(Arrays.equals(varNums, table.getDimensionNumbers()));
    assertTrue(Arrays.equals(varSizes, table.getDimensionSizes()));
    assertTrue(Arrays.equals(new int[] {}, emptyInputTable.getDimensionNumbers()));
    assertTrue(Arrays.equals(new int[] {}, emptyInputTable.getDimensionSizes()));
  }

  public void testGet() {
    assertEquals(1.0, table.getByDimKey(a1));
    assertEquals(2.0, table.getByDimKey(a2));
    assertEquals(2.0, table.getByDimKey(Arrays.copyOf(a2, a2.length)));

    assertEquals(0.0, table.getByDimKey(new int[] { 0, 0, 1 }));
    assertEquals(0.0, table.getByDimKey(new int[] { 0, 1, 0 }));
    assertEquals(0.0, table.getByDimKey(new int[] { 1, 0, 0 }));

    assertEquals(5.0, emptyInputTable.getByDimKey(new int[] {}));
    try {
      table.getByDimKey(new int[] { 0, 0 });
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException.");
  }
  
  public void testGetLog() {
    assertEquals(0.0, table.getLogByDimKey(a1));
    assertEquals(Math.log(2.0), table.getLogByDimKey(a2));
    assertEquals(Math.log(2.0), table.getLogByDimKey(Arrays.copyOf(a2, a2.length)));

    assertEquals(Double.NEGATIVE_INFINITY, table.getLogByDimKey(new int[] { 0, 0, 1 }));
    assertEquals(Double.NEGATIVE_INFINITY, table.getLogByDimKey(new int[] { 0, 1, 0 }));
    assertEquals(Double.NEGATIVE_INFINITY, table.getLogByDimKey(new int[] { 1, 0, 0 }));

    assertEquals(Math.log(5.0), emptyInputTable.getLogByDimKey(new int[] {}));
    try {
      table.getLogByDimKey(new int[] { 0, 0 });
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException.");
  }
  
  public void testKeyValueIterator() {
    Iterator<KeyValue> keyValueIterator = table.keyValueIterator();
    Set<int[]> intSet = Sets.newTreeSet(Ints.lexicographicalComparator());
    while (keyValueIterator.hasNext()) {
      KeyValue keyValue = keyValueIterator.next();
      assertEquals(table.getByDimKey(keyValue.getKey()), keyValue.getValue());
      intSet.add(Arrays.copyOf(keyValue.getKey(), 3));
    }
    assertTrue("Found " + intSet.size() + " keys, expected either 18 or 120", 
        intSet.size() == 17 || intSet.size() == 120);
  }
  
  public void testGetKeyInt() {
    assertEquals(31, table.dimKeyToKeyNum(new int[] {1, 2, 3}));
    assertTrue(Arrays.equals(new int[] {1, 2, 3}, table.keyNumToDimKey(31)));
  }
  
  public void testSlice() {
    Tensor slice = table.slice(new int[] {1, 4}, new int[] {2, 0});
    assertEquals(3.0, slice.getByDimKey(0));
    assertEquals(5.0, slice.getByDimKey(1));
    assertEquals(0.0, slice.getByDimKey(2));
    assertEquals(5, slice.getDimensionSizes()[0]);
    assertEquals(3, slice.getDimensionNumbers()[0]);
  }
  
  public void testSliceLeft1() {
    // Test a case where both the start and end index are in the tensor:
    Tensor slice = table.slice(new int[] {1}, new int[] {3});
    assertTrue(Arrays.equals(new int[]{5,4}, slice.getDimensionSizes()));
    assertTrue(Arrays.equals(new int[]{3,4}, slice.getDimensionNumbers()));
    assertEquals(5.0, slice.getByDimKey(1, 0));
    assertEquals(3.0, slice.getByDimKey(0, 0));
    assertEquals(0.0, slice.getByDimKey(0, 3));
    assertEquals(7.0, slice.getByDimKey(4, 3));
    assertTrue(4 == slice.size() || 20 == slice.size());
    
    // Test a case where neither start nor end are in the tensor:
    slice = table.slice(new int[] {1}, new int[] {1});
    assertTrue(Arrays.equals(new int[]{5,4}, slice.getDimensionSizes()));
    assertTrue(Arrays.equals(new int[]{3,4}, slice.getDimensionNumbers()));
    assertEquals(5.0, slice.getByDimKey(0, 3));
    assertEquals(0.0, slice.getByDimKey(1, 3));
    assertEquals(5.0, slice.getByDimKey(3, 0));
    assertEquals(0.0, slice.getByDimKey(3, 1));
    assertEquals(0.0, slice.getByDimKey(4, 3));
    assertTrue(2 == slice.size() || 20 == slice.size());    
  }
  
  public void testSliceEmpty() {
    Tensor slice = emptyInputTable.slice(new int[0], new int[0]);
    assertEquals(5.0, slice.getByDimKey(new int[0]));
  }
  
  public void testKeyPrefixIteratorEmpty() {
    Iterator<KeyValue> keyValueIterator = table.keyValuePrefixIterator(new int[] {1, 2});
    
    while (keyValueIterator.hasNext()) {
      KeyValue keyValue = keyValueIterator.next();
      // Both of these methods should be equivalent.
      assertEquals(0.0, keyValue.getValue());
      assertEquals(0.0, table.getByDimKey(keyValue.getKey()));
    }
  }
  
  public void testKeyPrefixIterator() {
    Iterator<KeyValue> keyValueIterator = table.keyValuePrefixIterator(new int[] {3});
    Set<int[]> intSet = Sets.newTreeSet(Ints.lexicographicalComparator());
    while (keyValueIterator.hasNext()) {
      intSet.add(Arrays.copyOf(keyValueIterator.next().getKey(), 3));
    }
    assertTrue(intSet.size() == 4 || intSet.size() == 20);
    assertTrue(intSet.contains(new int[] {3, 0, 0}));
    assertTrue(intSet.contains(new int[] {3, 0, 1}));
    assertTrue(intSet.contains(new int[] {3, 1, 0}));
    assertTrue(intSet.contains(new int[] {3, 4, 3}));
    
    keyValueIterator = table.keyValuePrefixIterator(new int[] {3, 0});
    intSet = Sets.newTreeSet(Ints.lexicographicalComparator());
    while (keyValueIterator.hasNext()) {
      intSet.add(Arrays.copyOf(keyValueIterator.next().getKey(), 3));
    }
    assertTrue(intSet.size() == 2 || intSet.size() == 4);
    assertTrue(intSet.contains(new int[] {3, 0, 0}));
    assertTrue(intSet.contains(new int[] {3, 0, 1}));
  }

  public void testElementwiseProductEmpty() {
    for (Tensor emptyTable : emptyTables) {
      Tensor expected = simpleMultiply(table, emptyTable);
      Tensor actual = table.elementwiseProduct(emptyTable);

      assertEquals(expected, actual);
    }
  }
  
  public void testElementwiseProductEmpty2() {
    for (Tensor emptyTable : emptyTables) {
      Tensor expected = simpleMultiply(emptyInputTable, emptyTable);
      Tensor actual = emptyInputTable.elementwiseProduct(emptyTable);

      assertEquals(expected, actual);
    }
  }

  public void testElementwiseProductMissingFirst() {
    for (Tensor missingFirst : missingFirsts) {
      Tensor expected = simpleMultiply(table, missingFirst);
      Tensor actual = table.elementwiseProduct(missingFirst);
      
      assertEquals(expected, actual);
    }
  }

  public void testElementwiseProductMissingMiddle() {
    for (Tensor missingMiddle : missingMiddles) {
      Tensor expected = simpleMultiply(table, missingMiddle);
      Tensor actual = table.elementwiseProduct(missingMiddle);

      assertEquals(expected, actual);
    }
  }

  public void testElementwiseProductMissingLast() {
    for (Tensor missingLast : missingLasts) {
      Tensor expected = simpleMultiply(table, missingLast);
      Tensor actual = table.elementwiseProduct(missingLast);

      assertEquals(expected, actual);
    }
  }
 
  public void testElementwiseAddition() {
    for (Tensor addTable : addTables) {
      Tensor actual = table.elementwiseAddition(addTable);

      assertEquals(2.0, actual.getByDimKey(a1));
      assertEquals(4.0, actual.getByDimKey(a2));
      assertEquals(3.0, actual.getByDimKey(new int[] {4, 0, 0}));
      assertEquals(5.0, actual.getByDimKey(new int[] {1, 0, 3}));
      assertEquals(0.0, actual.getByDimKey(new int[] {5, 1, 0}));
      assertTrue(Arrays.equals(varSizes, actual.getDimensionSizes()));
    }
  }
  
  public void testElementwiseMaximum() {
    for (Tensor addTable : addTables) {
      Tensor actual = table.elementwiseMaximum(addTable);
    
      assertEquals(1.0, actual.getByDimKey(a1));
      assertEquals(2.0, actual.getByDimKey(a2));
      assertEquals(6.0, actual.getByDimKey(new int[] {3, 0, 1}));
      assertEquals(5.0, actual.getByDimKey(new int[] {3, 1, 0}));
      assertEquals(0.0, actual.getByDimKey(new int[] {5, 1, 0}));
      assertTrue(Arrays.equals(varSizes, actual.getDimensionSizes()));
    }
  }

  public void testElementwiseInverse() {
    Tensor actual = table.elementwiseInverse();
    
    assertEquals(1.0, actual.getByDimKey(a1));
    assertEquals(1.0 / 2.0, actual.getByDimKey(a2));
    assertEquals(0.0, actual.getByDimKey(new int[] {5, 1, 0}));
    assertTrue(Arrays.equals(varSizes, actual.getDimensionSizes()));
  }
    
  public void testElementwiseLog() {
    Tensor actual = table.elementwiseLog();
    
    assertEquals(0.0, actual.getByDimKey(a1));
    assertEquals(Math.log(2.0), actual.getByDimKey(a2));
    assertEquals(Double.NEGATIVE_INFINITY, actual.getByDimKey(new int[] {5, 1, 0}));
    assertTrue(Arrays.equals(varSizes, actual.getDimensionSizes()));
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
    Tensor actual = table.relabelDimensions(new int[] {5, 6, 7});
    assertTrue(Arrays.equals(new int[] {5, 6, 7}, actual.getDimensionNumbers()));
    assertTrue(Arrays.equals(varSizes, actual.getDimensionSizes()));
    
    Function<KeyValue, List<Integer>> toList = new Function<KeyValue, List<Integer>>() {
      @Override
      public List<Integer> apply(KeyValue keyValue) {
        return Ints.asList(keyValue.getKey());
      }
    };
    
    assertEquals(Lists.newArrayList(Iterators.transform(table.keyValueIterator(), toList)),
          Lists.newArrayList(Iterators.transform(actual.keyValueIterator(), toList)));
  }
  
  public void testRelabelDimensions() {
    Tensor actual = table.relabelDimensions(new int[] {7, 5, 6});
    
    assertTrue(Arrays.equals(new int[] {5, 6, 7}, actual.getDimensionNumbers()));
    assertTrue(Arrays.equals(new int[] {5, 4, 6}, actual.getDimensionSizes()));
    
    assertEquals(table.size(), actual.size());
    
    Iterator<KeyValue> keyValueIter = actual.keyValueIterator();
    while (keyValueIter.hasNext()) {
      KeyValue keyValue = keyValueIter.next();
      int[] key = keyValue.getKey();
      int[] oldKey = new int[key.length];
      oldKey[0] = key[2];
      oldKey[2] = key[1];
      oldKey[1] = key[0];
      assertEquals(table.getByDimKey(oldKey), actual.getByDimKey(key));
      assertEquals(table.getByDimKey(oldKey), keyValue.getValue());
    }
  }
  
  public void testSerialization() {
    TensorProto proto = table.toProto();
    Tensor tableCopy = Tensors.fromProto(proto);
    
    assertTrue(Arrays.equals(tableCopy.getDimensionNumbers(), table.getDimensionNumbers()));
    assertTrue(Arrays.equals(tableCopy.getDimensionSizes(), table.getDimensionSizes()));

    Iterator<KeyValue> iter = table.keyValueIterator();
    Iterator<KeyValue> copyIter = tableCopy.keyValueIterator();
    while (iter.hasNext()) {
      assertTrue(copyIter.hasNext());
      
      KeyValue val = iter.next();
      KeyValue valCopy = copyIter.next();
      assertTrue(Arrays.equals(val.getKey(), valCopy.getKey()));
      assertEquals(val.getValue(), valCopy.getValue());
    }    
    assertFalse(copyIter.hasNext());
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
  private Tensor simpleMultiply(Tensor first, Tensor second) {
    TensorBuilder builder = tensorFactory.getBuilder(first.getDimensionNumbers(),
        first.getDimensionSizes());

    int firstInd = 0;
    int[] alignment = new int[second.getDimensionNumbers().length];
    for (int i = 0; i < second.getDimensionNumbers().length; i++) {
      while (first.getDimensionNumbers()[firstInd] < second.getDimensionNumbers()[i]) {
        firstInd++;
      }
      Preconditions.checkArgument(first.getDimensionNumbers()[firstInd] == second.getDimensionNumbers()[i]);
      alignment[i] = firstInd;
    }

    Iterator<KeyValue> firstIter = first.keyValueIterator();
    while (firstIter.hasNext()) {
      KeyValue firstKeyValue = firstIter.next();
      Iterator<KeyValue> secondIter = second.keyValueIterator();
      while (secondIter.hasNext()) {
        KeyValue secondKeyValue = secondIter.next();
        boolean equal = true;
        for (int i = 0; i < alignment.length; i++) {
          equal = equal && secondKeyValue.getKey()[i] == firstKeyValue.getKey()[alignment[i]];
        }

        if (equal) {
          builder.put(firstKeyValue.getKey(), firstKeyValue.getValue() * secondKeyValue.getValue());
        }
      }
    }
    return builder.build();
  }

  /**
   * Helper method for testing sumOutDimensions / maxOutDimensions.
   */
  private void runReduceTest(Tensor table, Set<Integer> dimsToEliminate) {
    Tensor expected = simpleReduce(table, dimsToEliminate, true);
    Tensor actual = table.sumOutDimensions(dimsToEliminate);
    assertEquals(expected, actual);
    expected = simpleReduce(table, dimsToEliminate, false);
    actual = table.maxOutDimensions(dimsToEliminate);
    assertEquals(expected, actual);
  }
  
  /**
   * This is a simple version of sum/max out dimensions algorithm. 
   */
  private Tensor simpleReduce(Tensor first, Set<Integer> dimsToEliminate,
      boolean useSum) {
    int[] currentDims = first.getDimensionNumbers();
    int[] currentSizes = first.getDimensionSizes();
    int[] newDimNums = new int[currentDims.length];
    int[] newDimSizes = new int[currentDims.length];
    Map<Integer, Integer> newDimIndices = Maps.newHashMap();    
    for (int i = 0; i < currentDims.length; i++) {
      if (!dimsToEliminate.contains(currentDims[i])) {
        newDimNums[newDimIndices.size()] = currentDims[i];
        newDimSizes[newDimIndices.size()] = currentSizes[i];
        newDimIndices.put(i, newDimIndices.size());
      }
    }
    
    TensorBuilder builder = tensorFactory.getBuilder(
        Arrays.copyOf(newDimNums, newDimIndices.size()),
        Arrays.copyOf(newDimSizes, newDimIndices.size()));
    Iterator<KeyValue> keyValueIter = first.keyValueIterator();
    while (keyValueIter.hasNext()) {
      KeyValue curKeyValue = keyValueIter.next();
      int[] newKey = new int[newDimIndices.size()];
      for (Map.Entry<Integer, Integer> entry : newDimIndices.entrySet()) {
        newKey[entry.getValue()] = curKeyValue.getKey()[entry.getKey()];
      }
      
      if (useSum) {
        builder.put(newKey, builder.getByDimKey(newKey) + curKeyValue.getValue());
      } else {
        builder.put(newKey, Math.max(builder.getByDimKey(newKey), curKeyValue.getValue()));
      }
    }
    return builder.build();
  }
}
