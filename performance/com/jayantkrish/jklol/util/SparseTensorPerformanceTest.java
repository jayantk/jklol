package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Iterator;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.testing.PerformanceTest;
import com.jayantkrish.jklol.testing.PerformanceTestCase;
import com.jayantkrish.jklol.testing.PerformanceTestRunner;

/**
 * Performance tests for {@link SparseTensor}.
 * 
 * @author jayant
 */
public class SparseTensorPerformanceTest extends PerformanceTestCase {

  double[][] assignmentNums;
  double[] firstTestArray,secondTestArray;
  
  SparseTensor table012, table01, table12;
  int[] varNums;

  public void setUp() {
    varNums = new int[] {0, 1, 2};

    SparseTensorBuilder builder = new SparseTensorBuilder(varNums);
    for (int i = 0; i < 100000; i++) {
      builder.put(new int[] {(i / 1000), (i / 100) % 10, i % 10}, 1.0);
    }
    table012 = builder.build();
    
    builder = new SparseTensorBuilder(new int[] {0, 1});
    for (int i = 0; i < 1000; i++) {
      builder.put(new int[] {i / 10, i % 10}, 1.0);
    }
    table01 = builder.build();

    builder = new SparseTensorBuilder(new int[] {1, 2});
    for (int i = 0; i < 1000; i++) {
      builder.put(new int[] {i / 10, i % 10}, 1.0);
    }
    table12 = builder.build();
    
    firstTestArray = new double[100000];
    secondTestArray = new double[100000];
    for (int i = 0; i < 100000; i++) {
      firstTestArray[i] = i;
      secondTestArray[i] = i + 1;
    }
  }

  @PerformanceTest(10)
  public void testProductLeftAligned() {
    table012.elementwiseProduct(table01);
  }

  @PerformanceTest(10)
  public void testProductLeftAlignedBig() {
    table012.elementwiseProduct(table012);
  }

  @PerformanceTest(10)
  public void testProductRightAligned() {
    table012.elementwiseProduct(table12);
  }
  
  @PerformanceTest(10)
  public void testProductDense() {
    double[] output = new double[firstTestArray.length];
    for (int i = 0; i < firstTestArray.length; i++) {
      output[i] = firstTestArray[i] * secondTestArray[i];
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
    Iterator<int[]> iter = table012.keyIterator();
    while (iter.hasNext()) {
      iter.next();
    }
  }
  
  @PerformanceTest
  public void testBuild() {
    SparseTensorBuilder builder = new SparseTensorBuilder(varNums);
    for (int i = 0; i < 100000; i++) {
      builder.put(new int[] {(i / 1000), (i / 100) % 10, i % 10}, 1.0);
    }
    builder.build();
  }
  
  @PerformanceTest
  public void testCount() {
    CountAccumulator<Integer> counter = CountAccumulator.create();
    for (int i = 0; i < 100000; i++) {
      counter.increment(i, 1.0);
    }
    
    CountAccumulator<Integer> counter2 = CountAccumulator.create();
    for (int i = 0; i < 100; i++) {
      counter2.increment(i, 1.0);
    }

    counter.increment(counter2);
  }
  
  @PerformanceTest
  public void testIncrementCount() {
    CountAccumulator<Integer> counter = CountAccumulator.create();
    for (int i = 0; i < 100000; i++) {
      counter.increment(i, 1.0);
    }

    for (int i = 0; i < 100; i++) {
      counter.increment(i, 1.0);
    }
  }
  
  @PerformanceTest
  public void testDefaultHashMap() {
    DefaultHashMap<Integer, Double> map = new DefaultHashMap<Integer, Double>(0.0);
    for (int i = 0; i < 100000; i++) {
      map.put(i, 1.0);
    }
  }
  
  @PerformanceTest
  public void testAssignmentCreation() {
    for (int i = 0; i < 10000; i++) {
      new Assignment(Ints.asList(varNums), Arrays.asList(new Integer[] {(i / 100), (i / 10) % 10, i % 10}));
    }
  }

  @PerformanceTest
  public void testSubAssignment() {
    Assignment a = new Assignment(Ints.asList(varNums), Arrays.asList(new Integer[] {0,0,0}));
    for (int i = 0; i < 10000; i++) {
      a.intersection(Arrays.asList(new Integer[] {0, 2}));
    }
  }
  
  public static void main(String[] args) {
    PerformanceTestRunner.run(new SparseTensorPerformanceTest());
  }
}