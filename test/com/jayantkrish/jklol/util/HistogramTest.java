package com.jayantkrish.jklol.util;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;

public class HistogramTest extends TestCase {
  
  Histogram<Integer> histogram;
  
  private final static int NUM_SAMPLES = 60000;
  private final static double TOLERANCE = 0.01;
  
  public void setUp() {
    histogram = new Histogram<Integer>(Ints.asList(0, 1, 2), new int[] {1, 3, 6});
  }
  
  public void testSample() {
    int[] counts = new int[3];
    
    for (int i = 0; i < NUM_SAMPLES; i++) {
      int sample = histogram.sample();
      counts[sample]++;
    }

    assertEquals(1.0 / 6.0, ((double) counts[0]) / NUM_SAMPLES, TOLERANCE);
    assertEquals(2.0 / 6.0, ((double) counts[1]) / NUM_SAMPLES, TOLERANCE);
    assertEquals(3.0 / 6.0, ((double) counts[2]) / NUM_SAMPLES, TOLERANCE);
  }
}
