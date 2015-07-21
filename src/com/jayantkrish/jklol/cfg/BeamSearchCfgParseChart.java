package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;

import com.jayantkrish.jklol.util.HeapUtils;

public class BeamSearchCfgParseChart {

  private final List<Object> terminals;
  private final int beamSize;

  // Together, chart and chartProbabilities are a collection of min-heaps. They
  // contain one min-heap per parse tree node. chartProbabilities contains the
  // values that the heap is sorted by, while chart contains a key for each
  // value.
  private final long[][][] chart;
  private final double[][][] chartProbabilities;
  private final int[] chartSizes;

  public BeamSearchCfgParseChart(List<Object> terminals, int beamSize) {
    this.terminals = terminals;
    this.beamSize = beamSize;

    this.chart = new long[terminals.size()][terminals.size()][beamSize + 1];
    this.chartProbabilities = new double[terminals.size()][terminals.size()][beamSize + 1];
    this.chartSizes = new int[terminals.size() * terminals.size()];
    Arrays.fill(chartSizes, 0);
  }

  public int chartSize() {
    return terminals.size();
  }
  
  public List<Object> getTerminals() {
    return terminals;
  }

  public int getBeamSize() {
    return beamSize;
  }

  /**
   * Gets the keys spanning the terminals from {@code spanStart} to
   * {@code spanEnd}, inclusive.
   * 
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public long[] getParseTreeKeysForSpan(int spanStart, int spanEnd) {
    return chart[spanStart][spanEnd];
  }

  public double[] getParseTreeProbsForSpan(int spanStart, int spanEnd) {
    return chartProbabilities[spanStart][spanEnd];
  }

  public int getNumParseTreeKeysForSpan(int spanStart, int spanEnd) {
    return chartSizes[spanEnd + (terminals.size() * spanStart)];
  }

  public void addParseTreeKeyForSpan(int spanStart, int spanEnd, long treeKey, double probability) {
    if (chartSizes[spanEnd + (terminals.size() * spanStart)] == beamSize &&
        probability <= chartProbabilities[spanStart][spanEnd][0]) {
      // Trees below the minimum probability can be ignored as long as the beam is full.
      return;
    }

    HeapUtils.offer(chart[spanStart][spanEnd], chartProbabilities[spanStart][spanEnd], 
        chartSizes[spanEnd + (terminals.size() * spanStart)], treeKey, probability);
    chartSizes[spanEnd + (terminals.size() * spanStart)]++;
    if (chartSizes[spanEnd + (terminals.size() * spanStart)] > beamSize) {
      HeapUtils.removeMin(chart[spanStart][spanEnd], chartProbabilities[spanStart][spanEnd], 
          chartSizes[spanEnd + (terminals.size() * spanStart)]);
      chartSizes[spanEnd + (terminals.size() * spanStart)]--;
    }
  }
}
