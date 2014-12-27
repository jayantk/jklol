package com.jayantkrish.jklol.util;

import java.util.Iterator;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.tensor.TensorFactory;
import com.jayantkrish.jklol.testing.PerformanceTest;
import com.jayantkrish.jklol.testing.PerformanceTestCase;

/**
 * Performance tests for generic {@link Tensor} operations.
 * 
 * @author jayant
 */
public abstract class TensorPerformanceTest extends PerformanceTestCase {

  private final TensorFactory tensorFactory;
  
  double[][] assignmentNums;
  double[] firstTestArray,secondTestArray;
  long[] firstArrayKeys,secondArrayKeys;
  
  Tensor table012, table01, table12, table012Copy;
  int[] varNums;
  
  public TensorPerformanceTest(TensorFactory tensorFactory) {
    this.tensorFactory = tensorFactory;
  }

  public void setUp() {
    varNums = new int[] {0, 1, 2};

    TensorBuilder builder = tensorFactory.getBuilder(varNums, new int[] {100, 100, 100});
    for (int i = 0; i < 1000000; i++) {
      builder.put(new int[] {(i / 10000), (i / 100) % 100, i % 100}, 1.0);
    }
    table012 = builder.build();
    table012Copy = builder.build();
    
    builder = tensorFactory.getBuilder(new int[] {0, 1}, new int[] {100, 100});
    for (int i = 0; i < 10000; i++) {
      builder.put(new int[] {i / 100, i % 100}, 1.0);
    }
    table01 = builder.build();

    builder = tensorFactory.getBuilder(new int[] {1, 2}, new int[] {100, 100});
    for (int i = 0; i < 10000; i++) {
      builder.put(new int[] {i / 100, i % 100}, 1.0);
    }
    table12 = builder.build();
    
    firstTestArray = new double[1000000];
    secondTestArray = new double[1000000];
    firstArrayKeys = new long[1000000];
    secondArrayKeys = new long[1000000];
    for (int i = 0; i < 100000; i++) {
      firstTestArray[i] = i;
      secondTestArray[i] = i + 1;
      firstArrayKeys[i] = i;
      secondArrayKeys[i] = i;
    }
  }
  
  @PerformanceTest(20)
  public void testProductSelf() {
    table012.elementwiseProduct(table012Copy);
  }

  @PerformanceTest(20)
  public void testProductLeftAligned() {
    table012.elementwiseProduct(table01);
  }

  @PerformanceTest(20)
  public void testProductRightAligned() {
    table012.elementwiseProduct(table12);
  }
  
  @PerformanceTest(20)
  public void testProductConstant() {
    table012.elementwiseProduct(3.0);
  }
  
  @PerformanceTest(20)
  public void testInnerProduct() {
    table012.innerProduct(table012Copy);
  }

  @PerformanceTest(20)
  public void testElementwiseAdditionSelf() {
    table012.elementwiseAddition(table012Copy);
  }
  
  @PerformanceTest(20)
  public void testSumRightAligned() {
    table012.sumOutDimensions(Ints.asList(2));
  }
  
  @PerformanceTest(20)
  public void testElementwiseExp() {
    table012.elementwiseExp();
  }
  
  @PerformanceTest(20)
  public void testElementwiseInverse() {
    table012.elementwiseInverse();
  }
  
  @PerformanceTest(20)
  public void testElementwiseLog() {
    table012.elementwiseLog();
  }
  
  @PerformanceTest(20)
  public void testSparseProductLowerBound() {
    double[] output = new double[firstTestArray.length];
    long[] outputKeys = new long[firstTestArray.length];
    for (int i = 0; i < firstTestArray.length && i < secondTestArray.length; i++) {
      output[i] = firstTestArray[i] * secondTestArray[i];
      outputKeys[i] = firstArrayKeys[i];
    }
  }
  
  @PerformanceTest(20)
  public void testMarginalLowerBound() {
    double[] output = new double[100];
    for (int i = 0; i < firstTestArray.length; i++) {
      output[i % 100] += firstTestArray[i];
    }
  }
  
  @PerformanceTest(20)
  public void testIterationLowerBound() {
    double foo[] = new double[firstTestArray.length];
    for (int i = 0; i < foo.length; i++) {
      foo[i] += 1;
    }
  }

  @PerformanceTest
  public void testRelabelDims() {
    table012.relabelDimensions(new int[] {3, 2, 1});
  }
  
  @PerformanceTest
  public void testRelabelDimsNoChange() {
    table012.relabelDimensions(new int[] {1, 2, 3});
  }

  @PerformanceTest
  public void testIteration() {
    Iterator<KeyValue> iter = table012.keyValueIterator();
    while (iter.hasNext()) {
      iter.next();
    }
  }
  
  @PerformanceTest
  public void testBuild() {
    TensorBuilder builder = tensorFactory.getBuilder(varNums, new int[] {100, 100, 10});
    for (int i = 0; i < 100000; i++) {
      builder.put(new int[] {(i / 1000), (i / 100) % 10, i % 10}, 1.0);
    }
    builder.build();
  }
}