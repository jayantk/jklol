package com.jayantkrish.jklol.ccg.chart;

import java.util.Arrays;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.util.IntMultimap;

public class CcgExactHashTableChart extends AbstractCcgChart {

  // A hash table for storing chart entries as they are inserted.
  // The indexes store which entries of the hash table have been used.
  private final ChartEntry[][][] chart;
  private final double[][][] probabilities;
  private final int[][][] populatedIndexes;
  private final int[][] numPopulatedIndexes;
  
  // Conversion of the hash table to a list of entries for fast
  // traversal.
  private final ChartEntry[][][] chartList;
  private final double[][][] probabilitiesList;
  private final int[][] chartSizes;
  private final IntMultimap[][] chartEntriesBySyntacticCategory;

  private int totalChartSize;

  private static final int NUM_INITIAL_SPAN_ENTRIES = 1000;

  public CcgExactHashTableChart(List<String> terminals, List<String> posTags, int maxChartSize) {
    super(terminals, posTags, maxChartSize);
    int numTerminals = terminals.size();

    this.chart = new ChartEntry[numTerminals][numTerminals][NUM_INITIAL_SPAN_ENTRIES];
    this.probabilities = new double[numTerminals][numTerminals][NUM_INITIAL_SPAN_ENTRIES];
    this.populatedIndexes = new int[numTerminals][numTerminals][NUM_INITIAL_SPAN_ENTRIES];
    this.numPopulatedIndexes = new int[numTerminals][numTerminals];

    this.chartList = new ChartEntry[numTerminals][numTerminals][];
    this.probabilitiesList = new double[numTerminals][numTerminals][];
    this.chartSizes = new int[numTerminals][numTerminals];

    this.chartEntriesBySyntacticCategory = new IntMultimap[numTerminals][numTerminals];

    this.totalChartSize = 0;
  }

  /**
   * Gets the best CCG parse for the given span. Returns {@code null}
   * if no parse was found.
   * 
   * @param spanStart
   * @param spanEnd
   * @param parser
   * @return
   */
  public CcgParse decodeBestParseForSpan(int spanStart, int spanEnd, CcgParser parser) {
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
      return decodeParseFromSpan(spanStart, spanEnd, maxEntryIndex, parser);
    }
  }

  @Override
  public CcgParse decodeBestParse(CcgParser parser) {
    return decodeBestParseForSpan(0, size() - 1, parser);
  }

  @Override
  public ChartEntry[] getChartEntriesForSpan(int spanStart, int spanEnd) {
    return chartList[spanStart][spanEnd];
  }

  @Override
  public double[] getChartEntryProbsForSpan(int spanStart, int spanEnd) {
    return probabilitiesList[spanStart][spanEnd];
  }

  @Override
  public int getNumChartEntriesForSpan(int spanStart, int spanEnd) {
    return chartSizes[spanStart][spanEnd];
  }
  
  @Override
  public IntMultimap getChartEntriesBySyntacticCategoryForSpan(int spanStart, int spanEnd) {
    return chartEntriesBySyntacticCategory[spanStart][spanEnd];
  }

  @Override
  public int getTotalNumChartEntries() {
    return totalChartSize;
  }

  @Override
  public void addChartEntryForSpan(ChartEntry entry, double probability, int spanStart,
      int spanEnd, DiscreteVariable syntaxVarType) {
    if (entryFilter != null) {
      probability *= Math.exp(entryFilter.apply(entry, spanStart, spanEnd, syntaxVarType));
    }

    if (probability != 0.0) {
      long entryHashCode = entry.getSyntaxHeadHashCode();

      int hashTableEntry = ((int) entryHashCode) % NUM_INITIAL_SPAN_ENTRIES;
      hashTableEntry = hashTableEntry < 0 ? -1 * hashTableEntry : hashTableEntry; 

      ChartEntry[] spanChart = chart[spanStart][spanEnd];
      double[] spanProbs = probabilities[spanStart][spanEnd];
      
      if (spanChart[hashTableEntry] == null || probability > spanProbs[hashTableEntry]) {
        if (spanChart[hashTableEntry] == null) {
          // This entry didn't exist before. Store its index for
          // fast enumeration later.
          int nextIndexIndex = numPopulatedIndexes[spanStart][spanEnd];
          populatedIndexes[spanStart][spanEnd][nextIndexIndex] = hashTableEntry;
          numPopulatedIndexes[spanStart][spanEnd]++;
        }

        spanChart[hashTableEntry] = entry;
        spanProbs[hashTableEntry] = probability;
      }
    }
  }

  @Override
  public void doneAddingChartEntriesForSpan(int spanStart, int spanEnd) {
    int[] indexes = populatedIndexes[spanStart][spanEnd];
    int numPopulated = numPopulatedIndexes[spanStart][spanEnd];

    ChartEntry[] hashTableEntries = chart[spanStart][spanEnd];
    double[] hashTableProbabilities = probabilities[spanStart][spanEnd];
    
    ChartEntry[] spanEntries = new ChartEntry[numPopulated];
    double[] spanProbabilities = new double[numPopulated];
    for (int i = 0; i < numPopulated; i++) {
      spanEntries[i] = hashTableEntries[indexes[i]];
      spanProbabilities[i] = hashTableProbabilities[indexes[i]];
    }

    chartList[spanStart][spanEnd] = spanEntries;
    probabilitiesList[spanStart][spanEnd] = spanProbabilities;
    chartSizes[spanStart][spanEnd] = numPopulated;
    chartEntriesBySyntacticCategory[spanStart][spanEnd] = aggregateBySyntacticType(
        spanEntries, spanEntries.length);

    totalChartSize += numPopulated;
  }

  @Override
  public void clearChartEntriesForSpan(int spanStart, int spanEnd) {
    totalChartSize -= chartSizes[spanStart][spanEnd];
    chartSizes[spanStart][spanEnd] = 0;
    numPopulatedIndexes[spanStart][spanEnd] = 0;
    chartEntriesBySyntacticCategory[spanStart][spanEnd] = null;
    Arrays.fill(chart[spanStart][spanEnd], null);
  }
}
