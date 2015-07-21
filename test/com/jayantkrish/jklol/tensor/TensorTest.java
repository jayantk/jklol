package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.AbstractTensorBase.DimensionSpec;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

/**
 * Implementation-independent test cases for tensor operations.
 * 
 * @author jayantk
 */
public abstract class TensorTest extends TestCase {

  private final TensorFactory tensorFactory;
  private final List<TensorFactory> allTensorFactories;
  
  protected int[] varNums, varSizes;
  protected Tensor table, vector, emptyInputTable;

  protected List<Tensor> smallTables, missingMiddles, missingFirsts,
    missingLasts, emptyTables, addTables, disjointTables, vectors, matrixInnerProductFirsts,
    matrixInnerProductMiddles, matrixInnerProductLasts;

  protected int[] a1, a2;
  
  private static enum ReduceType {
    SUM, MAX, LOG_SUM
  };
  
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
    builder.put(new int[] { 0, 3, 0 }, -3.0);
    builder.put(new int[] { 0, 3, 1 }, 4.0);
    builder.put(new int[] { 1, 3, 0 }, -5.0);
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
    builder.put(new int[] { 5, 4, 3 }, 3.0);
    table = builder.build();
    
    // Empty table is a table with no dimensions, which should behave like a
    // scalar.
    builder = tensorFactory.getBuilder(new int[] {}, new int[] {});
    builder.put(new int[] {}, 5.0);
    emptyInputTable = builder.build();

    TensorBuilder vectorBuilder = tensorFactory.getBuilder(new int[] {1}, new int[] {5});
    vectorBuilder.putByKeyNum(1, 1.0);
    vectorBuilder.putByKeyNum(4, 2.0);
    vector = vectorBuilder.build();

    smallTables = Lists.newArrayList();
    addTables = Lists.newArrayList();
    disjointTables = Lists.newArrayList();
    missingFirsts = Lists.newArrayList();
    missingMiddles = Lists.newArrayList();
    missingLasts = Lists.newArrayList();
    matrixInnerProductFirsts = Lists.newArrayList();
    matrixInnerProductMiddles = Lists.newArrayList();
    matrixInnerProductLasts = Lists.newArrayList();
    emptyTables = Lists.newArrayList();
    vectors = Lists.newArrayList();
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
      builder.put(new int[] { 5, 3 }, 7.0);
      builder.put(new int[] { 5, 4 }, 8.0);
      missingLasts.add(builder.build());
      
      builder = otherFactory.getBuilder(new int[] {1, 2}, new int[] {6, 3});
      builder.put(new int[] {0, 1}, 1.0);
      builder.put(new int[] {0, 2}, 2.0);
      builder.put(new int[] {1, 0}, 0.5);
      builder.put(new int[] {3, 0}, 2.0);
      matrixInnerProductFirsts.add(builder.build());
      
      builder = otherFactory.getBuilder(new int[] {3, 4}, new int[] {5, 3});
      builder.put(new int[] {0, 1}, 1.0);
      builder.put(new int[] {0, 2}, 2.0);
      builder.put(new int[] {1, 0}, 0.5);
      builder.put(new int[] {3, 0}, 2.0);
      matrixInnerProductMiddles.add(builder.build()); 

      builder = otherFactory.getBuilder(new int[] {4, 5}, new int[] {4, 3});
      builder.put(new int[] {0, 1}, 1.0);
      builder.put(new int[] {0, 2}, 2.0);
      builder.put(new int[] {1, 0}, 0.5);
      builder.put(new int[] {3, 0}, 2.0);
      matrixInnerProductLasts.add(builder.build()); 

      // Empty table is a table with no dimensions, which should behave like a
      // scalar.
      builder = otherFactory.getBuilder(new int[] {}, new int[] {});
      builder.put(new int[] {}, 5.0);
      emptyTables.add(builder.build());
      
      builder = otherFactory.getBuilder(new int[] {5, 7}, new int[] {2, 3});
      builder.put(new int[] {0, 0}, 1.0);
      builder.put(new int[] {1, 1}, 2.0);
      builder.put(new int[] {1, 2}, 3.0);
      disjointTables.add(builder.build());

      builder = otherFactory.getBuilder(new int[] {1}, new int[] {6});
      builder.put(new int[] {0}, 2.0);
      builder.put(new int[] {1}, 2.5);
      builder.put(new int[] {3}, 3.0);
      builder.put(new int[] {4}, 4.0);
      builder.put(new int[] {5}, 6.0);
      vectors.add(builder.build());
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
        intSet.size() == 18 || intSet.size() == 120);
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

  public void testSlice2() {
    Tensor slice = table.slice(new int[] {3, 4}, new int[] {4, 3});
    assertEquals(0.0, slice.getByDimKey(0));
    assertEquals(7.0, slice.getByDimKey(3));
    assertEquals(3.0, slice.getByDimKey(5));
    assertEquals(6, slice.getDimensionSizes()[0]);
    assertEquals(1, slice.getDimensionNumbers()[0]);
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
    assertEquals(-5.0, slice.getByDimKey(3, 0));
    assertEquals(0.0, slice.getByDimKey(3, 1));
    assertEquals(0.0, slice.getByDimKey(4, 3));
    assertTrue(2 == slice.size() || 20 == slice.size());    
  }
  
  public void testSliceEmpty() {
    Tensor slice = emptyInputTable.slice(new int[0], new int[0]);
    assertEquals(5.0, slice.getByDimKey(new int[0]));
  }

  public void testSliceAll() {
    Tensor slice = table.slice(new int[] {1, 3, 4}, new int[] {2, 0, 1});
    assertEquals(4.0, slice.getByDimKey(new int[0]));
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
      
  public void testFindKeysLargerThan() {
    Tensor indicator = table.findKeysLargerThan(4.5);

    assertEquals(6, indicator.size());
    
    assertEquals(0.0, indicator.getByDimKey(0, 0, 3));
    assertEquals(0.0, indicator.getByDimKey(0, 1, 3));
    assertEquals(0.0, indicator.getByDimKey(0, 3, 0));
    
    assertEquals(1.0, indicator.getByDimKey(1, 0, 3));
    assertEquals(1.0, indicator.getByDimKey(3, 1, 0));
    assertEquals(1.0, indicator.getByDimKey(3, 4, 3));
    assertEquals(1.0, indicator.getByDimKey(2, 1, 0));
    assertEquals(1.0, indicator.getByDimKey(4, 0, 1));
    assertEquals(1.0, indicator.getByDimKey(4, 1, 0));
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

      assertTensorEquals(expected, actual, 0.0);
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
  
  public void testElementwiseProductConstant() {
    Tensor actual = table.elementwiseProduct(2.0);
    Tensor expected = simpleMultiply(table, SparseTensor.getScalarConstant(2.0));

    assertEquals(expected, actual);
  }
  
  public void testElementwiseProductList() {
    Tensor actual = table.elementwiseProduct(missingLasts);
    Tensor expected = table;
    for (Tensor missingLast : missingLasts) {
      expected = simpleMultiply(expected, missingLast);
    }
    assertEquals(expected, actual);
  }
  
  public void testInnerProductRightAligned() {
    for (Tensor missingFirst : missingFirsts) {
      Tensor actual = table.innerProduct(missingFirst);
      Tensor expected = simpleReduce(simpleMultiply(table, missingFirst), 
          Sets.newHashSet(Ints.asList(missingFirst.getDimensionNumbers())), ReduceType.SUM);
      assertEquals(expected, actual);
    }
  }

  public void testInnerProductLeftAligned() {
    for (Tensor missingLast : missingLasts) {
      Tensor actual = table.innerProduct(missingLast);
      Tensor expected = simpleReduce(simpleMultiply(table, missingLast), 
          Sets.newHashSet(Ints.asList(missingLast.getDimensionNumbers())), ReduceType.SUM);      
      assertEquals(expected, actual);
    }
  }

  public void testInnerProductLeftAlignedVector() {
    for (Tensor vector : vectors) {
      Tensor actual = table.innerProduct(vector);
      Tensor expected = simpleReduce(simpleMultiply(table, vector), 
          Sets.newHashSet(Ints.asList(vector.getDimensionNumbers())), ReduceType.SUM);
      assertEquals(expected, actual);
    }
  }
  
  public void testInnerProductAllDims() {
    Tensor actual = table.innerProduct(table);
    Tensor expected = simpleReduce(simpleMultiply(table, table), 
          Sets.newHashSet(Ints.asList(table.getDimensionNumbers())), ReduceType.SUM);
    assertEquals(expected, actual);
  }

  public void testMatrixInnerProductLeftAligned() {
    for (Tensor matrixInnerProductFirst : matrixInnerProductFirsts) {
      Tensor actual = table.matrixInnerProduct(matrixInnerProductFirst);
      Tensor expected = simpleReduce(simpleMultiply(table, matrixInnerProductFirst),
          Sets.newHashSet(1), ReduceType.SUM);
      assertEquals(expected, actual);
    }
  }
  
  public void testMatrixInnerProductMiddleAligned() {
    Tensor relabeledTable = table.relabelDimensions(new int[] {1, 3, 5});
    for (Tensor matrixInnerProductMiddle : matrixInnerProductMiddles) {
      Tensor actual = relabeledTable.matrixInnerProduct(matrixInnerProductMiddle);
      Tensor expected = simpleReduce(simpleMultiply(relabeledTable, matrixInnerProductMiddle),
          Sets.newHashSet(3), ReduceType.SUM);
      assertEquals(expected, actual);
    }
  }

  public void testMatrixInnerProductRightAligned() {
    for (Tensor matrixInnerProductLast : matrixInnerProductLasts) {
      Tensor actual = table.matrixInnerProduct(matrixInnerProductLast);
      Tensor expected = simpleReduce(simpleMultiply(table, matrixInnerProductLast),
          Sets.newHashSet(4), ReduceType.SUM);
      assertEquals(expected, actual);
    }
  }

  public void testOuterProduct() {
    for (Tensor disjoint : disjointTables) {
      Tensor result = table.outerProduct(disjoint);
      assertEquals(5.0, result.getByDimKey(4, 1, 0, 0, 0));
      assertEquals(0.0, result.getByDimKey(4, 1, 0, 1, 0));
      assertEquals(10.0, result.getByDimKey(4, 1, 0, 1, 1));
      assertEquals(15.0, result.getByDimKey(4, 1, 0, 1, 2));
      assertEquals(0.0, result.getByDimKey(3, 1, 1, 1, 2));
      assertEquals(0.0, result.getByDimKey(3, 1, 1, 1, 0));
      assertEquals(3.0, result.getByDimKey(0, 0, 3, 0, 0));
      
      assertTrue(Arrays.equals(new int[] {1, 3, 4, 5, 7}, result.getDimensionNumbers()));
      assertTrue(Arrays.equals(new int[] {6, 5, 4, 2, 3}, result.getDimensionSizes()));
      
      // The size is different depending on whether the tensor is sparse or dense.
      assertTrue(result.size() == (18 * 3) || result.size() == (120 * 6));
    }
  }

  public void testOuterProduct2() {
    for (Tensor missingFirst : missingFirsts) {
      Tensor result = vector.outerProduct(missingFirst);
      
      assertEquals(0.0, result.getByDimKey(0, 0, 3));
      assertEquals(2.0, result.getByDimKey(1, 0, 3));
      assertEquals(0.0, result.getByDimKey(3, 0, 3));
      assertEquals(4.0, result.getByDimKey(4, 0, 3));
      assertEquals(0.0, result.getByDimKey(0, 1, 3));
      assertEquals(3.0, result.getByDimKey(1, 1, 3));
      assertEquals(6.0, result.getByDimKey(4, 1, 3));
      assertEquals(4.0, result.getByDimKey(1, 3, 0));
      assertEquals(0.0, result.getByDimKey(2, 3, 0));
      assertEquals(8.0, result.getByDimKey(4, 3, 0));

      assertTrue(Arrays.equals(new int[] {1, 3, 4}, result.getDimensionNumbers()));
      assertTrue(Arrays.equals(new int[] {5, 5, 4}, result.getDimensionSizes()));

      // The size is different depending on whether the tensor is sparse or dense.
      assertTrue(result.size() == (3 * 2) || result.size() == (20 * 5));
    }
  }
  
  public void testOuterProduct3() {
    Tensor relabeledVector = vector.relabelDimensions(new int[] {3});
    for (Tensor missingMiddle : missingMiddles) {
      Tensor result = relabeledVector.outerProduct(missingMiddle);
      
      assertEquals(0.0, result.getByDimKey(0, 0, 0));
      assertEquals(0.0, result.getByDimKey(0, 0, 3));
      assertEquals(0.0, result.getByDimKey(0, 1, 0));
      assertEquals(0.0, result.getByDimKey(0, 4, 0));
      assertEquals(0.0, result.getByDimKey(5, 4, 3));

      assertEquals(2.0, result.getByDimKey(0, 1, 3));
      assertEquals(4.0, result.getByDimKey(0, 4, 3));
      assertEquals(3.0, result.getByDimKey(1, 1, 3));
      assertEquals(6.0, result.getByDimKey(1, 4, 3));
      assertEquals(4.0, result.getByDimKey(3, 1, 0));
      assertEquals(8.0, result.getByDimKey(3, 4, 0));
      assertEquals(5.0, result.getByDimKey(4, 1, 0));
      assertEquals(10.0, result.getByDimKey(4, 4, 0));
      assertEquals(6.0, result.getByDimKey(4, 1, 1));
      assertEquals(12.0, result.getByDimKey(4, 4, 1));

      assertTrue(Arrays.equals(new int[] {1, 3, 4}, result.getDimensionNumbers()));
      assertTrue(Arrays.equals(new int[] {6, 5, 4}, result.getDimensionSizes()));

      // The size is different depending on whether the tensor is sparse or dense.
      assertTrue(result.size() == (5 * 2) || result.size() == (24 * 5) || result.size() == (24 * 2));
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
  
  public void testElementwiseAddition2() {
    Tensor actual = table.elementwiseAddition(2.0);

    assertEquals(3.0, actual.getByDimKey(a1));
    assertEquals(4.0, actual.getByDimKey(a2));
    assertEquals(2.0, actual.getByDimKey(new int[] {4, 0, 0}));
    assertEquals(7.0, actual.getByDimKey(new int[] {1, 0, 3}));
    assertEquals(2.0, actual.getByDimKey(new int[] {5, 1, 0}));
    assertTrue(Arrays.equals(varSizes, actual.getDimensionSizes()));
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
  
  public void testElementwiseSqrt() {
    Tensor actual = table.elementwiseSqrt();
    
    assertEquals(1.0, actual.getByDimKey(a1));
    assertEquals(Math.sqrt(2.0), actual.getByDimKey(a2));
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
  
  public void testSoftThreshold() {
    Tensor actual = table.softThreshold(4.0);
    
    assertEquals(0.0, actual.getByDimKey(0, 0, 1));
    assertEquals(0.0, actual.getByDimKey(0, 0, 3));
    assertEquals(0.0, actual.getByDimKey(0, 1, 3));
    assertEquals(1.0, actual.getByDimKey(1, 0, 3));
    assertEquals(-1.0, actual.getByDimKey(1, 3, 0));
    assertEquals(3.0, actual.getByDimKey(3, 4, 3));
  }
  
  public void testGetEntriesLargerThan() {
    Tensor actual = table.getEntriesLargerThan(5.0);
    assertEquals(0.0, actual.getByDimKey(0, 0, 0));
    assertEquals(0.0, actual.getByDimKey(0, 0, 3));
    assertEquals(1.0, actual.getByDimKey(1, 0, 3));
    assertEquals(0.0, actual.getByDimKey(1, 3, 0));
    assertEquals(1.0, actual.getByDimKey(3, 4, 3));
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

  public void testGetLargestValues() {
    long[] largestKeys = table.getLargestValues(3);
    assertEquals(3, largestKeys.length);
    assertTrue(Arrays.equals(new int[] {3, 4, 3}, table.keyNumToDimKey(largestKeys[0])));
    assertEquals(5.0, table.get(largestKeys[1]));
    assertEquals(5.0, table.get(largestKeys[2]));
  }

  /**
   * This is a simple version of the elementwise multiply algorithm which looks
   * at all pairs of keys in {@code first} and {@code second}.
   * 
   * @param first
   * @param second
   * @return
   */
  private Tensor simpleMultiply(Tensor first, Tensor second) {
    int[] firstDims = first.getDimensionNumbers();
    int[] secondDims = second.getDimensionNumbers();
    int[] secondToFirstAlignment = AbstractTensorBase.getDimensionAlignment(secondDims, firstDims);
    // System.out.println("secondToFirst: " + Arrays.toString(secondToFirstAlignment));
    
    DimensionSpec resultDimSpec = AbstractTensorBase.mergeDimensions(firstDims, first.getDimensionSizes(),
        secondDims, second.getDimensionSizes());
    int[] resultDims = resultDimSpec.getDimensionNumbers();
    int[] resultDimSizes = resultDimSpec.getDimensionSizes();
    
    int[] firstToResultAlignment = AbstractTensorBase.getDimensionAlignment(firstDims, resultDims);
    // System.out.println("firstToResult: " + Arrays.toString(firstToResultAlignment));
    int[] secondToResultAlignment = AbstractTensorBase.getDimensionAlignment(secondDims, resultDims);
    // System.out.println("secondToResult: " + Arrays.toString(secondToResultAlignment));

    TensorBuilder builder = tensorFactory.getBuilder(resultDims, resultDimSizes);

    Iterator<KeyValue> firstIter = first.keyValueIterator();
    while (firstIter.hasNext()) {
      KeyValue firstKeyValue = firstIter.next();
      Iterator<KeyValue> secondIter = second.keyValueIterator();
      while (secondIter.hasNext()) {
        KeyValue secondKeyValue = secondIter.next();
        boolean equal = true;
        for (int i = 0; i < secondToFirstAlignment.length; i++) {
          if (secondToFirstAlignment[i] != -1) {
            equal = equal && secondKeyValue.getKey()[i] == firstKeyValue.getKey()[secondToFirstAlignment[i]];
          }
        }

        if (equal) {
          int[] resultKey = new int[resultDims.length];
          for (int i = 0; i < firstDims.length; i++) {
            int alignedIndex = firstToResultAlignment[i];
            if (alignedIndex != -1) {
              resultKey[alignedIndex] = firstKeyValue.getKey()[i];
            }
          }
          
          for (int i = 0; i < secondDims.length; i++) {
            int alignedIndex = secondToResultAlignment[i];
            if (alignedIndex != -1) {
              resultKey[alignedIndex] = secondKeyValue.getKey()[i];
            }
          }

          builder.put(resultKey, firstKeyValue.getValue() * secondKeyValue.getValue());
        }
      }
    }
    return builder.build();
  }

  /**
   * Helper method for testing sumOutDimensions / maxOutDimensions / logSumOutDimensions.
   */
  private void runReduceTest(Tensor table, Set<Integer> dimsToEliminate) {
    Tensor expected = simpleReduce(table, dimsToEliminate, ReduceType.SUM);
    Tensor actual = table.sumOutDimensions(dimsToEliminate);
    assertEquals(expected, actual);
 
    expected = simpleReduce(table, dimsToEliminate, ReduceType.LOG_SUM);
    actual = table.logSumOutDimensions(dimsToEliminate);    
    assertTensorEquals(expected, actual, 10e-8);

    Backpointers actualBackpointers = new Backpointers();
    expected = simpleReduce(table, dimsToEliminate, ReduceType.MAX);
    actual = table.maxOutDimensions(dimsToEliminate, actualBackpointers);
    assertEquals(expected, actual);
    
    // Test that the returned backpointers point to keys with the correct values.
    Tensor indicator = actualBackpointers.getOldKeyIndicatorTensor();
    Iterator<KeyValue> iter = actual.keyValueIterator();
    while (iter.hasNext()) {
      KeyValue k = iter.next();
      long oldKeyNum = actualBackpointers.getBackpointer(actual.dimKeyToKeyNum(k.getKey()));
      assertEquals(actual.getByDimKey(k.getKey()), table.get(oldKeyNum));
      assertEquals(1.0, indicator.get(oldKeyNum));
      assertEquals(1.0, indicator.getByDimKey(table.keyNumToDimKey(oldKeyNum)));
    }
  }
  
  /**
   * This is a simple version of sum/max out dimensions algorithm. 
   */
  private Tensor simpleReduce(Tensor first, Set<Integer> dimsToEliminate, ReduceType type) {
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
    if (type == ReduceType.MAX) {
      builder.increment(Double.NEGATIVE_INFINITY);
    }
    
    Iterator<int[]> allKeyIter = new IntegerArrayIterator(first.getDimensionSizes(), new int[0]);
    while (allKeyIter.hasNext()) {
      int[] key = allKeyIter.next();
      double value = first.getByDimKey(key);
      int[] newKey = new int[newDimIndices.size()];
      for (Map.Entry<Integer, Integer> entry : newDimIndices.entrySet()) {
        newKey[entry.getValue()] = key[entry.getKey()];
      }
      
      if (type == ReduceType.SUM) {
        builder.put(newKey, builder.getByDimKey(newKey) + value);
      } else if (type == ReduceType.MAX) {
        if (value >= builder.getByDimKey(newKey)) { 
          builder.put(newKey, value);
        }
      } else if (type == ReduceType.LOG_SUM) {
        builder.put(newKey, builder.getByDimKey(newKey) + Math.exp(value));
      }
    }
    
    if (type == ReduceType.LOG_SUM) {
      return builder.build().elementwiseLog();
    } else {
      return builder.build();
    }
  }
  
  private void assertTensorEquals(Tensor expected, Tensor actual, double tolerance) {
    assertTrue(Arrays.equals(expected.getDimensionNumbers(), actual.getDimensionNumbers()));
    
    Iterator<KeyValue> iter = actual.keyValueIterator();
    while (iter.hasNext()) {
      KeyValue k = iter.next();
      // System.out.println(k);
      assertEquals(expected.getByDimKey(k.getKey()), actual.getByDimKey(k.getKey()), tolerance);
    }
  }
}
