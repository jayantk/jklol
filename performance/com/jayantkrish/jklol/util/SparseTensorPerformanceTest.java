package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Iterator;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;

/**
 * Performance tests for {@link SparseTensor}.
 * 
 * @author jayant
 */
public class SparseTensorPerformanceTest extends TestCase {

  double[][] assignmentNums;
  double[] arrayTest;
  
  SparseTensor table012, table01, table12;
  int[] varNums;

  public void setUp() {
    assignmentNums = new double[10000][3];
    arrayTest = new double[10000]; 
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
  }
  
  public void testProductLeftAligned() {
    System.out.println("testProductLeftAligned");
    long start = System.currentTimeMillis();
    
    table012.elementwiseProduct(table01);
    
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }
  
  public void testProductLeftAlignedBig() {
    System.out.println("testProductLeftAlignedBig");
    long start = System.currentTimeMillis();

    table012.elementwiseProduct(table012);

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }

  
  public void testProductRightAligned() {
    System.out.println("testProductRightAligned");
    long start = System.currentTimeMillis();
    
    table012.elementwiseProduct(table12);
    
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }

  public void testRelabelDims() {
    System.out.println("testRelabelDims");
    long start = System.currentTimeMillis();
    
    table012.relabelDimensions(new int[] {3, 2, 1});
    
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }

  public void testIteration() {
    System.out.println("testIteration");
    long start = System.currentTimeMillis();

    Iterator<int[]> iter = table012.keyIterator();
    while (iter.hasNext()) {
      iter.next();
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }
  
  public void testBuild() {
    System.out.println("testBuild");
    long start = System.currentTimeMillis();

    SparseTensorBuilder builder = new SparseTensorBuilder(varNums);
    for (int i = 0; i < 100000; i++) {
      builder.put(new int[] {(i / 1000), (i / 100) % 10, i % 10}, 1.0);
    }
    builder.build();
    
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }
  
  public void testDefaultHashMap() {
    System.out.println("testDefaultHashMap");
    long start = System.currentTimeMillis();

    DefaultHashMap map = new DefaultHashMap<Integer, Double>(0.0);
    for (int i = 0; i < 100000; i++) {
      map.put(i, 1.0);
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }

  public void testAssignmentCreation() {
    System.out.println("testAssignmentCreation");
    long start = System.currentTimeMillis();

    for (int i = 0; i < 10000; i++) {
      new Assignment(Ints.asList(varNums), Arrays.asList(new Integer[] {(i / 100), (i / 10) % 10, i % 10}));
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }

  public void testSubAssignment() {
    System.out.println("testSubAssignment");
    long start = System.currentTimeMillis();
    Assignment a = new Assignment(Ints.asList(varNums), Arrays.asList(new Integer[] {0,0,0}));
    for (int i = 0; i < 10000; i++) {
      a.subAssignment(Arrays.asList(new Integer[] {0, 2}));
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }
}