package com.jayantkrish.jklol.tensor;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;

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
    assertEquals(18, table.size());
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
    for (KeyValue keyValue : Lists.newArrayList(emptyInputTable.keyValueIterator())) {
      actualKeys.add(Ints.asList(keyValue.getKey()));
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
  
  /**
   * Test tensor products with over 2^32 keys
   */
  public void testLargeTensorProduct() {
    TensorBuilder firstBuilder = new SparseTensorBuilder(new int[] {0, 1, 2, 3}, 
        new int[] {10000, 10000, 10000, 10000});
    TensorBuilder secondBuilder = new SparseTensorBuilder(new int[] {2}, 
        new int[] {10000});
    for (int i = 1; i < 1000; i++) {
      firstBuilder.put(new int[] {i, i, i, i}, i);
      secondBuilder.put(new int[] {i}, i);
    }
    assertEquals(999, firstBuilder.size());
    assertEquals(999, secondBuilder.size());
    
    Tensor firstTensor = firstBuilder.build();
    Tensor secondTensor = secondBuilder.build();
    
    assertEquals(999, firstTensor.size());
    assertEquals(999, secondTensor.size());
    
    Tensor result = firstTensor.elementwiseProduct(secondTensor);
    assertEquals(999, result.size());
    
    for (int i = 0; i < 100; i++) {
      assertEquals((double) i * i, result.getByDimKey(i, i, i, i));
    }
  }
  
  /**
   * Test tensor addition with over 2^32 keys
   */
  public void testLargeTensorAddition() {
    TensorBuilder firstBuilder = new SparseTensorBuilder(new int[] {0, 1, 2, 3}, 
        new int[] {10000, 10000, 10000, 10000});
    TensorBuilder secondBuilder = new SparseTensorBuilder(new int[] {0, 1, 2, 3}, 
        new int[] {10000, 10000, 10000, 10000});
    firstBuilder.put(new int[] {999, 998, 997, 996}, 1.0);
    secondBuilder.put(new int[] {0, 1, 2, 3}, 2.0);
    for (int i = 0; i < 100; i++) {
      firstBuilder.put(new int[] {i, i, i, i}, i);
      secondBuilder.put(new int[] {i, i, i, i}, i);
    }
    Tensor firstTensor = firstBuilder.build();
    Tensor secondTensor = secondBuilder.build();
    
    Tensor result = firstTensor.elementwiseAddition(secondTensor);
    for (int i = 0; i < 100; i++) {
      assertEquals((double) i + i, result.getByDimKey(i, i, i, i));
    }
    assertEquals(1.0, result.getByDimKey(999, 998, 997, 996));
    assertEquals(2.0, result.getByDimKey(0, 1, 2, 3));
  }

  /**
   * Test reducing dimensions with over 2^32 keys
   */
  public void testLargeTensorSum() {
    TensorBuilder firstBuilder = new SparseTensorBuilder(new int[] {0, 1, 2, 3}, 
        new int[] {10000, 10000, 10000, 10000});
    firstBuilder.put(new int[] {99, 998, 99, 99}, 1.0);
    firstBuilder.put(new int[] {99, 998, 97, 99}, 3.0);
    firstBuilder.put(new int[] {0, 1, 0, 0}, 2.0);
    for (int i = 0; i < 100; i++) {
      firstBuilder.put(new int[] {i, i, i, i}, i);
    }
    Tensor firstTensor = firstBuilder.build();
    
    Tensor result = firstTensor.sumOutDimensions(Ints.asList(1));
    for (int i = 1; i < 99; i++) {
      assertEquals((double) i, result.getByDimKey(i, i, i));
    }
    assertEquals(2.0, result.getByDimKey(0, 0, 0));
    assertEquals(3.0, result.getByDimKey(99, 97, 99));
    assertEquals(100.0, result.getByDimKey(99, 99, 99));
  }
  
  public void testDiagonal() {
    Tensor tensor = SparseTensor.diagonal(new int[] {0, 1, 2}, new int[] {4, 6, 5}, 2.0);
    
    for (int i = 0; i < 4; i++) {
      assertEquals(2.0, tensor.getByDimKey(i, i, i));
    }
    assertEquals(0.0, tensor.getByDimKey(0, 2, 3));
    assertEquals(0.0, tensor.getByDimKey(0, 3, 4));
    assertEquals(0.0, tensor.getByDimKey(1, 2, 3));
  }

  /**
   * Ensure that when two tensors are added, keys that sum to
   * 0 are removed from the result.
   */
  public void testAdditionSparse() {
    Tensor tensor = SparseTensor.vector(2, 4, new double[] {0, 2, 3, 0});
    Tensor tensor2 = SparseTensor.vector(2, 4, new double[] {0, -2, 4, 1});
    
    Tensor result = tensor.elementwiseAddition(tensor2);
    assertEquals(2, result.size());
  }
}
