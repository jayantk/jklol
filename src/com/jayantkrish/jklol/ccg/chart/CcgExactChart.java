package com.jayantkrish.jklol.ccg.chart;

import java.util.Arrays;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.models.DiscreteVariable;

/**
 * CCG chart for performing exact inference.
 * 
 * @author jayantk
 */
public class CcgExactChart extends AbstractCcgChart {

  private final ChartEntry[][][] chart;
  private final double[][][] probabilities;
  private final int[][] chartSizes;

  private final ChartFilter entryFilter;

  private static final int NUM_INITIAL_SPAN_ENTRIES = 100;

  public CcgExactChart(List<String> terminals, List<String> posTags,
      int[] wordDistances, int[] puncDistances, int[] verbDistances, ChartFilter entryFilter) {
    super(terminals, posTags, wordDistances, puncDistances, verbDistances, entryFilter);
    int numTerminals = terminals.size();
    this.chart = new ChartEntry[numTerminals][numTerminals][];
    this.probabilities = new double[numTerminals][numTerminals][];
    this.chartSizes = new int[numTerminals][numTerminals];

    this.entryFilter = entryFilter;
  }

  /**
   * Gets the best CCG parse for the given span. Returns {@code null}
   * if no parse was found.
   * 
   * @param spanStart
   * @param spanEnd
   * @param numParses
   * @param parser
   * @param syntaxVarType
   * @return
   */
  public CcgParse decodeBestParseForSpan(int spanStart, int spanEnd, int numParses,
      CcgParser parser, DiscreteVariable syntaxVarType) {
    double maxProb = -1;
    int maxEntryIndex = -1;
    double[] probs = getChartEntryProbsForSpan(spanStart, spanEnd);
    for (int i = 0; i < chartSizes[spanStart][spanEnd]; i++) {
      if (probs[i] > maxProb) {
        maxProb = probs[i];
        maxEntryIndex = i;
      }
    }

    if (maxEntryIndex == -1) {
      // No parses.
      return null;
    } else {
      return decodeParseFromSpan(spanStart, spanEnd, maxEntryIndex, parser, syntaxVarType);
    }
  }

  @Override
  public ChartEntry[] getChartEntriesForSpan(int spanStart, int spanEnd) {
    return chart[spanStart][spanEnd];
  }

  @Override
  public double[] getChartEntryProbsForSpan(int spanStart, int spanEnd) {
    return probabilities[spanStart][spanEnd];
  }

  @Override
  public int getNumChartEntriesForSpan(int spanStart, int spanEnd) {
    return chartSizes[spanStart][spanEnd];
  }

  @Override
  public void addChartEntryForSpan(ChartEntry entry, double probability, int spanStart,
      int spanEnd, DiscreteVariable syntaxVarType) {
    if (probability != 0.0 && (entryFilter == null || entryFilter.apply(entry, spanStart, spanEnd, syntaxVarType))) {
      if (chart[spanStart][spanEnd] == null) {
        chart[spanStart][spanEnd] = new ChartEntry[NUM_INITIAL_SPAN_ENTRIES];
        probabilities[spanStart][spanEnd] = new double[NUM_INITIAL_SPAN_ENTRIES];
        chartSizes[spanStart][spanEnd] = 0;
      }

      int entryHeadedSyntax = entry.getHeadedSyntax();

      int spanSize = chartSizes[spanStart][spanEnd];
      ChartEntry[] spanChart = chart[spanStart][spanEnd];
      double[] spanProbs = probabilities[spanStart][spanEnd];
      for (int i = 0; i < spanSize; i++) {
        ChartEntry other = spanChart[i];
        if (other.getHeadedSyntax() == entryHeadedSyntax && unfilledDepsEqual(entry, other)
            && assignmentEqual(entry, other)) {
          // Both entries have the same syntactic category and
          // semantics. Retain the entry with the highest probability.
          if (probability > spanProbs[i]) {
            // New entry better than old entry. Replace it.
            spanChart[i] = entry;
            spanProbs[i] = probability;
          } else {
            // The chart contains at most one entry with each
            // syntactic category and semantics. Hence, this entry
            // cannot be added since the chart already has a better
            // entry.
            return;
          }
        }
      }

      // There is no existing entry with the same syntax and
      // semantics. Add the given entry as a new entry.
      if (spanSize == spanChart.length) {
        // Resize the chart spans to ensure space for the new entries.
        chart[spanStart][spanEnd] = Arrays.copyOf(spanChart, spanChart.length * 2);
        probabilities[spanStart][spanEnd] = Arrays.copyOf(spanProbs, spanChart.length * 2);
      }

      chart[spanStart][spanEnd][spanSize] = entry;
      probabilities[spanStart][spanEnd][spanSize] = probability;
      chartSizes[spanStart][spanEnd] = spanSize + 1;
    }
  }

  @Override
  public void clearChartEntriesForSpan(int spanStart, int spanEnd) {
    chartSizes[spanStart][spanEnd] = 0;
    // This second part is unnecessary, but makes debugging easier.
    Arrays.fill(chart[spanStart][spanEnd], null);
  }
  
  /**
   * Checks if two chart entries have the same collection of unfilled dependencies.
   * 
   * @param first
   * @param second
   * @return
   */
  private static final boolean unfilledDepsEqual(ChartEntry first, ChartEntry second) {
    long[] firstDeps = first.getUnfilledDependencies();
    long[] secondDeps = second.getUnfilledDependencies();
    
    if (firstDeps.length != secondDeps.length) {
      return false;
    }
    
    long[] firstDepsCopy = Arrays.copyOf(firstDeps, firstDeps.length);
    long[] secondDepsCopy = Arrays.copyOf(secondDeps, secondDeps.length);
    Arrays.sort(firstDepsCopy);
    Arrays.sort(secondDepsCopy);
    
    for (int i = 0; i < firstDepsCopy.length; i++) {
      if (firstDepsCopy[i] != secondDepsCopy[i]) {
        return false;
      }
    }
    return true;
  }
  
  private static final boolean assignmentEqual(ChartEntry first, ChartEntry second) {
    first.getAssignmentIndexes();
  }
}
