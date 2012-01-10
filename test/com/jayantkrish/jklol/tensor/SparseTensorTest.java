package com.jayantkrish.jklol.tensor;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

/**
 * Unit tests for {@link SparseTensor}.
 * 
 * @author jayantk
 */
public class SparseTensorTest extends TensorTest {

  public SparseTensorTest() {
    super(SparseTensorBuilder.getFactory());
  }
  
  public void testSize() {
    assertEquals(16, table.size());
    assertEquals(1, emptyInputTable.size());
  }
  
  public void testAssignmentIterator() {
    Set<List<Integer>> expectedKeys = Sets.newHashSet();
    Set<List<Integer>> actualKeys = Sets.newHashSet();
    /*
    expectedKeys.add(Ints.asList(a1));
    expectedKeys.add(Ints.asList(a2));
    for (int[] key : Lists.newArrayList(smallTable.keyIterator())) {
      actualKeys.add(Ints.asList(key));
    }
    assertEquals(expectedKeys, actualKeys);
    */

    expectedKeys = Sets.newHashSet();
    expectedKeys.add(Lists.<Integer> newArrayList());
    actualKeys.clear();
    for (int[] key : Lists.newArrayList(emptyInputTable.keyIterator())) {
      actualKeys.add(Ints.asList(key));
    }
    assertEquals(expectedKeys, actualKeys);
  }
      
  public void testVector() {
    Tensor tensor = SparseTensor.vector(2, 4, new double[] {0, 2, 3, 0});
    assertEquals(2, tensor.size());
    assertEquals(2.0, tensor.getByDimKey(1));
    assertEquals(3.0, tensor.getByDimKey(2));
    assertEquals(0.0, tensor.getByDimKey(3));
  }
  
  public void testScalarConstant() {
    Tensor constant = SparseTensor.getScalarConstant(10);
    Tensor vector = SparseTensor.vector(2, 4, new double[] {0, 2, 3, 0});
    Tensor result = vector.elementwiseProduct(constant);
    assertEquals(0.0, result.get(0));
    assertEquals(20.0, result.get(1));
  }
}
