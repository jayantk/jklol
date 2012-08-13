package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.ccg.CcgCategory.DependencyStructure;
import com.jayantkrish.jklol.util.HeapUtils;

public class CcgChart {

  private final List<Object> terminals;
  private final int beamSize;

  private final ChartEntry[][][] chart;
  private final double[][][] probabilities;
  private final int[] chartSizes;

  public CcgChart(List<? extends Object> terminals, int beamSize) {
    this.terminals = ImmutableList.copyOf(terminals);
    this.beamSize = beamSize;

    int n = terminals.size();
    this.chart = new ChartEntry[n][n][beamSize + 1];
    this.probabilities = new double[n][n][beamSize + 1];
    this.chartSizes = new int[n * n];
    Arrays.fill(chartSizes, 0);
  }

  public int size() {
    return terminals.size();
  }
  
  public CcgParse decodeParseFromSpan(int spanStart, int spanEnd, int rootBeamIndex) {
    ChartEntry entry = chart[spanStart][spanEnd][rootBeamIndex];

    if (entry.isTerminal()) {
      return CcgParse.forTerminal(entry.getCategory(), probabilities[spanStart][spanEnd][rootBeamIndex]);
    } else {
      CcgParse left = decodeParseFromSpan(entry.getLeftSpanStart(), entry.getLeftSpanEnd(), entry.getLeftChartIndex());
      CcgParse right = decodeParseFromSpan(entry.getRightSpanStart(), entry.getRightSpanEnd(), entry.getRightChartIndex());

      double nodeProb = probabilities[spanStart][spanEnd][rootBeamIndex] / (left.getSubtreeProbability() * right.getSubtreeProbability());
      
      return CcgParse.forNonterminal(entry.getCategory(), entry.getDependencies(), nodeProb, left, right);
    }
  }

  public CcgCategory[] getParseTreesForSpan(int spanStart, int spanEnd) {
    CcgCategory[] categories = new CcgCategory[beamSize];
    int size = getNumParseTreesForSpan(spanStart, spanEnd);
    for (int i = 0; i < size; i++) {
      categories[i] = chart[spanStart][spanEnd][i].getCategory();
    }
    return categories;
  }

  public double[] getParseTreeProbsForSpan(int spanStart, int spanEnd) {
    return probabilities[spanStart][spanEnd];
  }

  public int getNumParseTreesForSpan(int spanStart, int spanEnd) {
    return chartSizes[spanEnd + (terminals.size() * spanStart)];
  }

  public void addParseTreeForSpan(CcgCategory result, List<DependencyStructure> deps,
      double probability, int leftSpanStart, int leftSpanEnd, int leftChartIndex, 
      int rightSpanStart, int rightSpanEnd, int rightChartIndex, 
      int spanStart, int spanEnd) {
    ChartEntry entry = new ChartEntry(result, deps, leftSpanStart, leftSpanEnd, leftChartIndex,
        rightSpanStart, rightSpanEnd, rightChartIndex);

    offerEntry(entry, probability, spanStart, spanEnd);
  }

  public void addParseTreeForTerminalSpan(CcgCategory result, double probability, 
      int spanStart, int spanEnd) {
    ChartEntry entry = new ChartEntry(result, spanStart, spanEnd);
    
    offerEntry(entry, probability, spanStart, spanEnd);
  }

  private final void offerEntry(ChartEntry entry, double probability, int spanStart, int spanEnd) {
    HeapUtils.offer(chart[spanStart][spanEnd], probabilities[spanStart][spanEnd],
        chartSizes[spanEnd + (terminals.size() * spanStart)], entry, probability);
    chartSizes[spanEnd + (terminals.size() * spanStart)]++;
    
    if (chartSizes[spanEnd + (terminals.size() * spanStart)] > beamSize) {
      HeapUtils.removeMin(chart[spanStart][spanEnd], probabilities[spanStart][spanEnd], 
          chartSizes[spanEnd + (terminals.size() * spanStart)]);
      chartSizes[spanEnd + (terminals.size() * spanStart)]--;
    }
  }

  private static class ChartEntry {
    private final CcgCategory category;
    private final List<DependencyStructure> deps;

    private final boolean isTerminal;

    private final int leftSpanStart;
    private final int leftSpanEnd;
    private final int leftChartIndex;

    private final int rightSpanStart;
    private final int rightSpanEnd;
    private final int rightChartIndex;

    public ChartEntry(CcgCategory category, List<DependencyStructure> deps,
        int leftSpanStart, int leftSpanEnd, int leftChartIndex,
        int rightSpanStart, int rightSpanEnd, int rightChartIndex) {
      this.category = Preconditions.checkNotNull(category);
      this.deps = Preconditions.checkNotNull(deps);

      isTerminal = false;

      this.leftSpanStart = leftSpanStart;
      this.leftSpanEnd = leftSpanEnd;
      this.leftChartIndex = leftChartIndex;

      this.rightSpanStart = rightSpanStart;
      this.rightSpanEnd = rightSpanEnd;
      this.rightChartIndex = rightChartIndex;
    }

    public ChartEntry(CcgCategory category, int spanStart, int spanEnd) {
      this.category = Preconditions.checkNotNull(category);
      this.deps = null;

      isTerminal = true;

      // Use the leftSpan to represent the spanned terminal.
      this.leftSpanStart = spanStart;
      this.leftSpanEnd = spanEnd;
      this.leftChartIndex = -1;
      
      this.rightSpanStart = -1;
      this.rightSpanEnd = -1;
      this.rightChartIndex = -1;
    }
    
    public CcgCategory getCategory() {
      return category;
    }
    
    public List<DependencyStructure> getDependencies() {
      return deps;
    }
    
    public boolean isTerminal() {
      return isTerminal;
    }

    public int getLeftSpanStart() {
      return leftSpanStart;
    }

    public int getLeftSpanEnd() {
      return leftSpanEnd;
    }

    public int getLeftChartIndex() {
      return leftChartIndex;
    }

    public int getRightSpanStart() {
      return rightSpanStart;
    }

    public int getRightSpanEnd() {
      return rightSpanEnd;
    }

    public int getRightChartIndex() {
      return rightChartIndex;
    }
  }
}