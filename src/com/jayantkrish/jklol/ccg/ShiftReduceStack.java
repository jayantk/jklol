package com.jayantkrish.jklol.ccg;

import com.jayantkrish.jklol.ccg.chart.ChartEntry;

/**
 * Stack state of a shift-reduce CCG parser.
 *  
 * @author jayantk
 */
public class ShiftReduceStack {
  public final int spanStart;
  public final int spanEnd;
  public final int chartEntryIndex;
  public final ChartEntry entry;
  public final double entryProb;

  public final ShiftReduceStack previous;

  public final int size;
  public final double totalProb;

  public ShiftReduceStack(int spanStart, int spanEnd, int chartEntryIndex, ChartEntry entry,
      double entryProb, ShiftReduceStack previous) {
    this.spanStart = spanStart;
    this.spanEnd = spanEnd;
    this.chartEntryIndex = chartEntryIndex;
    this.entry = entry;
    this.entryProb = entryProb;

    if (previous == null) {
      this.size = 0;
      this.totalProb = entryProb;
    } else {
      this.size = 1 + previous.size;
      this.totalProb = previous.totalProb * entryProb;
    }

    this.previous = previous;
  }

  public static ShiftReduceStack empty() {
    return new ShiftReduceStack(-1, -1, -1, null, 1.0, null);
  }

  public ShiftReduceStack push(int spanStart, int spanEnd, int chartEntryIndex, ChartEntry entry, double prob) {
    return new ShiftReduceStack(spanStart, spanEnd, chartEntryIndex, entry, prob, this);
  }

  @Override
  public String toString() {
    String prevString = previous == null ? "" : previous.toString();
    return spanStart + "," + spanEnd + " : " + prevString;
  }
}
