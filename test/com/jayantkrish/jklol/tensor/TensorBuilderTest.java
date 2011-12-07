package com.jayantkrish.jklol.tensor;

import junit.framework.TestCase;

/**
 * Test cases for all kinds of {@code TensorBuilder}s. To test an implementation
 * of {@code TensorBuilder}, subclass this and provide the appropriate factory
 * to the constructor.
 * 
 * @author jayantk
 */
public abstract class TensorBuilderTest extends TestCase {

  private final TensorFactory tensorFactory;

  int[] dimNums, dimSizes;
  TensorBuilder builder, otherBuilder, wrongDimensionBuilder;

  private static final int[] KEY0 = new int[] { 0, 0, 0 };
  private static final int[] KEY1 = new int[] { 0, 1, 2 };
  private static final int[] KEY2 = new int[] { 1, 2, 0 };
  private static final int[] KEY3 = new int[] { 2, 2, 3 };

  public TensorBuilderTest(TensorFactory tensorFactory) {
    this.tensorFactory = tensorFactory;
  }

  public void setUp() {
    dimNums = new int[] { 0, 2, 3 };
    dimSizes = new int[] { 4, 3, 5 };

    builder = tensorFactory.getBuilder(dimNums, dimSizes);
    builder.put(KEY1, 1.0);
    builder.put(KEY2, 2.0);
    
    otherBuilder = tensorFactory.getBuilder(dimNums, dimSizes);
    otherBuilder.put(KEY1, 3.0);
    otherBuilder.put(KEY3, 4.0);
    
    wrongDimensionBuilder = tensorFactory.getBuilder(new int[] {0, 1, 3}, new int[] {0, 1, 3});
  }

  public void testGet() {
    assertEquals(0.0, builder.get(KEY0));
    assertEquals(1.0, builder.get(KEY1));
    assertEquals(2.0, builder.get(KEY2));
    
    builder.put(KEY2, 4.0);
    assertEquals(4.0, builder.get(KEY2));
  }
  
  public void testGetInvalid1() {
    try {
      builder.get(new int[] {0, 1});
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
  
  public void testGetOutOfBounds1() {
    try {
      builder.get(new int[] {5, 2, 4});
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
  
  public void testGetOutOfBounds2() {
    try {
      builder.get(new int[] {0, -1, 3});
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
  
  public void testPutRepresentationExposure() {
    int[] test = new int[] {2, 2, 2};
    builder.put(test, 7.0);
    test[2] = 3;
    assertEquals(0.0, builder.get(test));
    assertEquals(7.0, builder.get(new int[] {2, 2, 2}));
  }
  
  public void testIncrementWithMultiplier() {
    builder.incrementWithMultiplier(otherBuilder, 2.0);

    assertEquals(0.0, builder.get(KEY0));
    assertEquals(7.0, builder.get(KEY1));
    assertEquals(2.0, builder.get(KEY2));
    assertEquals(8.0, builder.get(KEY3));
  }
  
  public void testIncrementInvalid() {
    try {
      builder.increment(wrongDimensionBuilder);
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
  
  public void testIncrementConstant() {
    builder.increment(5.0);
    assertEquals(5.0, builder.get(KEY0));
    assertEquals(6.0, builder.get(KEY1));
    assertEquals(7.0, builder.get(KEY2));
    assertEquals(5.0, builder.get(new int[] {3, 2, 4}));
  }
  
  public void testIncrementEntry() {
    builder.incrementEntry(3.0, KEY0);
    builder.incrementEntry(3.0, KEY2);
    assertEquals(3.0, builder.get(KEY0));
    assertEquals(1.0, builder.get(KEY1));
    assertEquals(5.0, builder.get(KEY2));
    assertEquals(0.0, builder.get(KEY3));
  }
  
  public void testMultiply() {
    builder.multiply(otherBuilder);
    assertEquals(0.0, builder.get(KEY0));
    assertEquals(3.0, builder.get(KEY1));
    assertEquals(0.0, builder.get(KEY2));
    assertEquals(0.0, builder.get(KEY3));
  }
  
  public void testMultiplyConstant() {
    builder.multiply(2.0);
    assertEquals(0.0, builder.get(KEY0));
    assertEquals(2.0, builder.get(KEY1));
    assertEquals(4.0, builder.get(KEY2));
    assertEquals(0.0, builder.get(KEY3));
  }
      
  public void testMultiplyInvalid() {
    try {
      builder.multiply(wrongDimensionBuilder);
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
  
  public void testBuild() {
    Tensor tensor = builder.build();
    assertEquals(0.0, tensor.get(KEY0));
    assertEquals(1.0, tensor.get(KEY1));
    assertEquals(2.0, tensor.get(KEY2));
  }
  
  public void testGetL2Norm() {
    assertEquals(Math.sqrt(5.0), builder.getL2Norm(), 0.0001); 
  }
}
