package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.SparseOutcomeTable;

/**
 * Performance tests for {@link SparseOutcomeTable}.
 * 
 * @author jayant
 */
public class SparseOutcomeTablePerformanceTest extends TestCase {

  double[][] assignmentNums;
  double[] arrayTest;
  
  SparseOutcomeTable<Double> table;
  List<Integer> varNums;

  public void setUp() {
    assignmentNums = new double[10000][3];
    arrayTest = new double[10000]; 
    varNums = Arrays.asList(new Integer[] {0, 1, 2});
    table = new SparseOutcomeTable<Double>(varNums);
  }

  public void testAssignmentCreation() {
    System.out.println("testAssignmentCreation");
    long start = System.currentTimeMillis();

    for (int i = 0; i < 10000; i++) {
      new Assignment(varNums, Arrays.asList(new Integer[] {(i / 100), (i / 10) % 10, i % 10}));
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }

  public void testPut() {
    System.out.println("testPut");
    long start = System.currentTimeMillis();

    for (int i = 0; i < 10000; i++) {
      table.put(new Assignment(varNums, Arrays.asList(new Integer[] {(i / 100), (i / 10) % 10, i % 10})),
          1.0);
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }
  
  public void testPutArray() {
    System.out.println("testPutArray");
    long start = System.currentTimeMillis();

    for (int i = 0; i < 10000; i++) {
      assignmentNums[i][0] = i / 100;
      assignmentNums[i][1] = (i / 10) % 10;
      assignmentNums[i][2] = i % 10;
      arrayTest[i] = 1.0; 
    }
    
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }
  
  public void testPutVariableArray() {
    System.out.println("testPutVariableArray");
    long start = System.currentTimeMillis();

    List<int[]> assignments = Lists.newArrayList();
    List<Double> values = Lists.newArrayList();
    
    for (int i = 0; i < 10000; i++) {
      assignments.add(new int[] {i / 100, (i/10) % 10, i % 10});
      values.add(1.0);
    }
    
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms"); 
  }

  public void testPutAssignmentSet() {
    System.out.println("testPutAssignmentSet");
    long start = System.currentTimeMillis();
    for (int i = 0; i < 10000; i++) {
      Assignment a = new Assignment(varNums, 
          Arrays.asList(new Integer[] {i / 100, (i / 10) % 10, i % 10}));
      table.put(a, 1.0);
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }


  public void testPutSameAssignment() {
    System.out.println("testPutSameAssignment");
    Assignment a = new Assignment(varNums, Arrays.asList(new Integer[] {0, 0, 0}));
    long start = System.currentTimeMillis();

    for (int i = 0; i < 10000; i++) {
      table.put(a, 1.0);
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }


  public void testIteration() {
    for (int i = 0; i < 10000; i++) {
      table.put(new Assignment(varNums, Arrays.asList(new Integer[] {(i / 100), (i / 10) % 10, i % 10})),
          1.0);
    }
    System.out.println("testIteration");
    long start = System.currentTimeMillis();

    Iterator<Assignment> iter = table.assignmentIterator();
    while (iter.hasNext()) {
      iter.next();
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }

  public void testSubAssignment() {
    System.out.println("testSubAssignment");
    long start = System.currentTimeMillis();
    Assignment a = new Assignment(varNums, Arrays.asList(new Integer[] {0,0,0}));
    for (int i = 0; i < 10000; i++) {
      a.subAssignment(Arrays.asList(new Integer[] {0, 2}));
    }

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Elapsed: " + elapsed + " ms");
  }
}