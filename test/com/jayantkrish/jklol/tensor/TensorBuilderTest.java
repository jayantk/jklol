package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;

/**
 * Test cases for all kinds of {@code TensorBuilder}s. To test an implementation
 * of {@code TensorBuilder}, subclass this and provide the appropriate factory
 * to the constructor.
 * 
 * @author jayantk
 */
public abstract class TensorBuilderTest extends TestCase {

  private final TensorFactory tensorFactory;
  private final List<TensorFactory> allTensorFactories;

  int[] dimNums, dimSizes;
  TensorBuilder builder;
  
  List<TensorBuilder> otherBuilders, wrongDimensionBuilders;

  private static final int[] KEY0 = new int[] { 0, 0, 0 };
  private static final int[] KEY1 = new int[] { 0, 1, 2 };
  private static final int[] KEY2 = new int[] { 1, 2, 0 };
  private static final int[] KEY3 = new int[] { 2, 2, 3 };
  private static final int[] KEY4 = new int[] { 3, 2, 3 };

  public TensorBuilderTest(TensorFactory tensorFactory) {
    this.tensorFactory = tensorFactory;
    this.allTensorFactories = Lists.newArrayList(SparseTensorBuilder.getFactory(), DenseTensorBuilder.getFactory());
  }

  public void setUp() {
    dimNums = new int[] { 0, 2, 3 };
    dimSizes = new int[] { 4, 3, 5 };

    builder = tensorFactory.getBuilder(dimNums, dimSizes);
    builder.put(KEY2, 2.0);
    builder.put(KEY1, 1.0);
    
    otherBuilders = Lists.newArrayList();
    for (TensorFactory otherFactory : allTensorFactories) {
      TensorBuilder otherBuilder = otherFactory.getBuilder(dimNums, dimSizes);
      otherBuilder.put(KEY1, 3.0);
      otherBuilder.put(KEY3, 4.0);
      otherBuilders.add(otherBuilder);
    }
    
    wrongDimensionBuilders = Lists.newArrayList();
    for (TensorFactory otherFactory : allTensorFactories) {
      wrongDimensionBuilders.add(otherFactory.getBuilder(new int[] {0, 1, 3}, new int[] {1, 1, 3}));
    }
  }

  public void testGet() {
    assertEquals(0.0, builder.getByDimKey(KEY0));
    assertEquals(1.0, builder.getByDimKey(KEY1));
    assertEquals(2.0, builder.getByDimKey(KEY2));
    
    builder.put(KEY2, 4.0);
    assertEquals(4.0, builder.getByDimKey(KEY2));
  }
  
  public void testGetInvalid1() {
    try {
      builder.getByDimKey(new int[] {0, 1});
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
  
  public void testGetOutOfBounds1() {
    try {
      builder.getByDimKey(new int[] {5, 2, 4});
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
  
  public void testGetOutOfBounds2() {
    try {
      builder.getByDimKey(new int[] {0, -1, 3});
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
  
  public void testKeyValueIterator() {
    Iterator<KeyValue> keyValueIterator = builder.keyValueIterator();
    Set<int[]> intSet = Sets.newTreeSet(Ints.lexicographicalComparator());
    while (keyValueIterator.hasNext()) {
      KeyValue keyValue = keyValueIterator.next();
      assertEquals(builder.getByDimKey(keyValue.getKey()), keyValue.getValue());
      intSet.add(Arrays.copyOf(keyValue.getKey(), 3));
    }
    assertTrue(intSet.size() == 2 || intSet.size() == 60);
  }
  
  public void testPutRepresentationExposure() {
    int[] test = new int[] {2, 2, 2};
    builder.put(test, 7.0);
    test[2] = 3;
    assertEquals(0.0, builder.getByDimKey(test));
    assertEquals(7.0, builder.getByDimKey(new int[] {2, 2, 2}));
  }
  
  public void testIncrementWithMultiplier() {
    for (int i = 0; i < otherBuilders.size(); i++) {
      builder.incrementWithMultiplier(otherBuilders.get(i), 2.0);

      assertEquals(0.0, builder.getByDimKey(KEY0));
      assertEquals(0.0, builder.get(builder.dimKeyToKeyNum(KEY0)));
      assertEquals(7.0 + (6.0 * i), builder.getByDimKey(KEY1));
      assertEquals(7.0 + (6.0 * i), builder.get(builder.dimKeyToKeyNum(KEY1)));
      assertEquals(2.0, builder.getByDimKey(KEY2));
      assertEquals(2.0, builder.get(builder.dimKeyToKeyNum(KEY2)));
      assertEquals(8.0 * (i + 1), builder.getByDimKey(KEY3));
      assertEquals(8.0 * (i + 1), builder.get(builder.dimKeyToKeyNum(KEY3)));
    }
  }
  
  public void testIncrementInvalid() {
    for (TensorBuilder wrongDimensionBuilder : wrongDimensionBuilders) {
      try {
        builder.increment(wrongDimensionBuilder);
      } catch (IllegalArgumentException e) {
        continue;
      }
      fail("Expected IllegalArgumentException");
    }
  }
  
  public void testIncrementConstant() {
    builder.increment(5.0);
    assertEquals(5.0, builder.getByDimKey(KEY0));
    assertEquals(6.0, builder.getByDimKey(KEY1));
    assertEquals(7.0, builder.getByDimKey(KEY2));
    assertEquals(5.0, builder.getByDimKey(new int[] {3, 2, 4}));
  }
  
  public void testIncrementEntry() {
    builder.incrementEntry(3.0, KEY0);
    builder.incrementEntry(3.0, KEY2);
    assertEquals(3.0, builder.getByDimKey(KEY0));
    assertEquals(1.0, builder.getByDimKey(KEY1));
    assertEquals(5.0, builder.getByDimKey(KEY2));
    assertEquals(0.0, builder.getByDimKey(KEY3));
  }
  
  public void testMultiply() {
    for (int i = 0; i < otherBuilders.size(); i++) {
      builder.multiply(otherBuilders.get(i));
      assertEquals(0.0, builder.getByDimKey(KEY0));
      assertEquals(Math.pow(3.0, i + 1), builder.getByDimKey(KEY1));
      assertEquals(0.0, builder.getByDimKey(KEY2));
      assertEquals(0.0, builder.getByDimKey(KEY3));
    }
  }
  
  public void testMultiplyConstant() {
    builder.multiply(2.0);
    assertEquals(0.0, builder.getByDimKey(KEY0));
    assertEquals(2.0, builder.getByDimKey(KEY1));
    assertEquals(4.0, builder.getByDimKey(KEY2));
    assertEquals(0.0, builder.getByDimKey(KEY3));
  }
      
  public void testMultiplyInvalid() {
    for (TensorBuilder wrongDimensionBuilder : wrongDimensionBuilders) {
      try {
        builder.multiply(wrongDimensionBuilder);
      } catch (IllegalArgumentException e) {
        continue;
      }
      fail("Expected IllegalArgumentException");
    }
  }
  
  public void testSoftThreshold() {
    builder.put(KEY0, -1.0);
    builder.put(KEY3, -3.0);
    builder.softThreshold(1.5);

    assertEquals(0.0, builder.getByDimKey(KEY0));
    assertEquals(0.0, builder.getByDimKey(KEY1));
    assertEquals(0.5, builder.getByDimKey(KEY2));
    assertEquals(-1.5, builder.getByDimKey(KEY3));
    assertEquals(0.0, builder.getByDimKey(KEY4));
  }
  
  public void testInnerProduct() {
    for (int i = 0; i < otherBuilders.size(); i++) {
      assertEquals(3.0, builder.innerProduct(otherBuilders.get(i)));
    }
    
    assertEquals(builder.getL2Norm() * builder.getL2Norm(), builder.innerProduct(builder), 0.00000001); 
  }
  
  public void testExp() {
    builder.exp();
    assertEquals(1.0, builder.getByDimKey(KEY0), 0.0001);
    assertEquals(Math.exp(1.0), builder.getByDimKey(KEY1), 0.0001);
    assertEquals(Math.exp(2.0), builder.getByDimKey(KEY2), 0.0001);
    assertEquals(1.0, builder.getByDimKey(KEY3), 0.0001);    
  }
  
  public void testBuild() {
    Tensor tensor = builder.build();
    assertEquals(0.0, tensor.getByDimKey(KEY0));
    assertEquals(1.0, tensor.getByDimKey(KEY1));
    assertEquals(2.0, tensor.getByDimKey(KEY2));
  }
  
  public void testGetL2Norm() {
    assertEquals(Math.sqrt(5.0), builder.getL2Norm(), 0.0001); 
  }
  
  public void testGetTrace() {
    assertEquals(3.0, builder.getTrace(), 0.0001);
  }
}
