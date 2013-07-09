package com.jayantkrish.jklol.ccg.chart;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.HeapUtils;

/**
 * Data structure for performing beam search inference with a CCG.
 * 
 * @author jayant
 */
public class CcgBeamSearchChart extends AbstractCcgChart {

  private final int beamSize;
  private final int numTerminals;

  private final ChartEntry[][][] chart;
  private final double[][][] probabilities;
  private final int[] chartSizes;

  private final ChartFilter entryFilter;

  /**
   * Creates a CCG chart for storing the current state of a beam
   * search trying to parse {@code terminals}.
   * 
   * @param terminals
   * @param posTags
   * @param puncCount
   * @param verbCount
   * @param beamSize
   * @param entryFilter filter for discarding portions of the beam.
   * May be {@code null}, in which case all beam entries are retained.
   */
  public CcgBeamSearchChart(List<String> terminals, List<String> posTags, int[] wordDistances,
      int[] puncDistances, int[] verbDistances, int beamSize, ChartFilter entryFilter) {
    super(terminals, posTags, wordDistances, puncDistances, verbDistances, entryFilter);
    this.beamSize = beamSize;

    numTerminals = terminals.size();
    this.chart = new ChartEntry[numTerminals][numTerminals][beamSize + 1];
    this.probabilities = new double[numTerminals][numTerminals][beamSize + 1];
    this.chartSizes = new int[numTerminals * numTerminals];
    Arrays.fill(chartSizes, 0);

    this.entryFilter = entryFilter;
  }

  /**
   * Gets the size of the beam, which is the maximum number of parses
   * to retain any span during beam search.
   * 
   * @return
   */
  public int getBeamSize() {
    return beamSize;
  }

  /**
   * Gets the {@code numParses} best CCG parses spanning
   * {@code spanStart} to {@code spanEnd}.
   * 
   * @param spanStart
   * @param spanEnd
   * @param numParses
   * @param parser
   * @param syntaxVarType
   * @return
   */
  public List<CcgParse> decodeBestParsesForSpan(int spanStart, int spanEnd, int numParses,
      CcgParser parser, DiscreteVariable syntaxVarType) {
    // Perform a heap sort on the array indexes paired with the
    // probabilities.
    double[] probsCopy = ArrayUtils.copyOf(probabilities[spanStart][spanEnd], probabilities[spanStart][spanEnd].length);
    Integer[] chartEntryIndexes = new Integer[probabilities[spanStart][spanEnd].length];
    for (int i = 0; i < chartEntryIndexes.length; i++) {
      chartEntryIndexes[i] = i;
    }

    // Heaps are min-heaps, so we throw away the initial entries.
    // Then the remaining entries are the best parses.
    List<CcgParse> bestParses = Lists.newArrayList();
    int numChartEntries = getNumChartEntriesForSpan(spanStart, spanEnd);
    while (numChartEntries > 0) {
      if (numChartEntries <= numParses) {
        bestParses.add(decodeParseFromSpan(spanStart, spanEnd, chartEntryIndexes[0],
            parser, syntaxVarType));
      }

      HeapUtils.removeMin(chartEntryIndexes, probsCopy, numChartEntries);
      numChartEntries--;
    }

    Collections.reverse(bestParses);
    return bestParses;
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
    return chartSizes[spanEnd + (numTerminals * spanStart)];
  }

  @Override
  public void addChartEntryForSpan(ChartEntry entry, double probability, int spanStart,
      int spanEnd, DiscreteVariable syntaxVarType) {
    if (probability != 0.0 && (entryFilter == null || entryFilter.apply(entry, spanStart, spanEnd, syntaxVarType))) {
      offerEntry(entry, probability, spanStart, spanEnd);
    }
  }

  @Override
  public void clearChartEntriesForSpan(int spanStart, int spanEnd) {
    chartSizes[spanEnd + (numTerminals * spanStart)] = 0;
    // This second part is unnecessary, but makes debugging easier.
    Arrays.fill(chart[spanStart][spanEnd], null);
  }

  /**
   * Adds a chart entry to the heap for {@code spanStart} to
   * {@code spanEnd}. This operation implements beam truncation by
   * discarding the minimum probability entry when a heap reaches the
   * beam size.
   */
  private final void offerEntry(ChartEntry entry, double probability, int spanStart, int spanEnd) {
    HeapUtils.offer(chart[spanStart][spanEnd], probabilities[spanStart][spanEnd],
        chartSizes[spanEnd + (numTerminals * spanStart)], entry, probability);
    chartSizes[spanEnd + (numTerminals * spanStart)]++;

    if (chartSizes[spanEnd + (numTerminals * spanStart)] > beamSize) {
      HeapUtils.removeMin(chart[spanStart][spanEnd], probabilities[spanStart][spanEnd],
          chartSizes[spanEnd + (numTerminals * spanStart)]);
      chartSizes[spanEnd + (numTerminals * spanStart)]--;
    }
  }

  public static ChartEntry[] copyChartEntryArray(ChartEntry[] entries, int numEntries) {
    ChartEntry[] returnValue = new ChartEntry[numEntries];
    for (int i = 0; i < numEntries; i++) {
      returnValue[i] = entries[i];
    }
    return returnValue;
  }
}
