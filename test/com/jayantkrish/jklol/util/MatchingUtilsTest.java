package com.jayantkrish.jklol.util;

import java.util.Arrays;

import junit.framework.TestCase;

public class MatchingUtilsTest extends TestCase {

  public void testSimple() {
    double[][] scores = new double[][] {
        {2.0, 3.0, 0.0},
        {3.0, 2.0, 0.0},
        {0.0, 2.0, 3.0}};
    runTest(scores, new int[] {1, 0, 2});
  }
  
  public void testHarder() {
    double[][] scores = new double[][] {
        {2.0, 3.0, 0.0},
        {0.0, 3.0, 0.0},
        {0.0, 2.0, 3.0}};
    runTest(scores, new int[] {0, 1, 2});
  }
  
  public void testEvenHarder() {
    double[][] scores = new double[][] {
        {2.0, 3.0, 1.5},
        {0.0, 3.0, 0.0},
        {3.0, 1.0, 0.0}};
    runTest(scores, new int[] {2, 1, 0});
  }
  
  public void testMoreColsThanRows() {
    double[][] scores = new double[][] {
        {2.0, 4.0, 1.5, 3.0},
        {0.0, 3.0, 0.0, 1.0},
        {3.0, 1.0, 0.0, 2.0}};
    runTest(scores, new int[] {3, 1, 0});
  }
  
  public void testMoreRowsThanCols() {
    double[][] scores = new double[][] {
        {2.0, 0.0, 3.0},
        {4.0, 3.0, 1.0},
        {1.5, 0.0, 0.0},
        {3.0, 1.0, 2.0}};
    runTest(scores, new int[] {2, 1, -1, 0});
  }
  
  public void testZeros() {
    double[][] scores = new double[][] {
    {0.0000, 0.0000, 0.0000, 0.0000}, 
    {0.0049, 0.0002, 0.0000, 0.0000},
    {0.0000, 0.0000, 0.0000, 0.0000},
    {0.0000, 0.0000, 0.0000, 0.0000},
    {0.0000, 0.0000, 0.0000, 0.0000},
    {0.0000, 0.0000, 0.0006, 0.0000},
    {0.0000, 0.0000, 0.0000, 0.0000},
    {0.0000, 0.0000, 0.0000, 0.0006},
    {0.0000, 0.0000, 0.0000, 0.0000}};
    runTest(scores, new int[] {1, 0, -1, -1, -1, 2, -1, 3, -1});
  }

  private void runTest(double[][] scores, int[] expected) {
    int[] assignment = MatchingUtils.maxMatching(scores, 0.0001);
    assertTrue("Expected: " + Arrays.toString(expected) + " got: " + Arrays.toString(assignment),
        Arrays.equals(expected, assignment));
  }
}
