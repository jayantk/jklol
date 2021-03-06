package com.jayantkrish.jklol.ccg.chart;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.HeapUtils;
import com.jayantkrish.jklol.util.IntMultimap;

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
  
  private final IntMultimap[][] chartEntriesBySyntacticCategory;

  private int totalChartSize;

  /**
   * Creates a CCG chart for storing the current state of a beam
   * search trying to parse {@code terminals}.
   * 
   * @param terminals
   * @param posTags
   * @param beamSize
   */
  public CcgBeamSearchChart(AnnotatedSentence sentence, int maxChartSize, int beamSize) {
    super(sentence, maxChartSize);
    this.beamSize = beamSize;

    numTerminals = sentence.size();
    this.chart = new ChartEntry[numTerminals][numTerminals][beamSize + 1];
    this.probabilities = new double[numTerminals][numTerminals][beamSize + 1];
    this.chartSizes = new int[numTerminals * numTerminals];
    Arrays.fill(chartSizes, 0);

    this.chartEntriesBySyntacticCategory = new IntMultimap[numTerminals][numTerminals];

    this.totalChartSize = 0;
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
   * @return
   */
  public List<CcgParse> decodeBestParsesForSpan(int spanStart, int spanEnd, int numParses,
      CcgParser parser) {
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
        bestParses.add(decodeParseFromSpan(spanStart, spanEnd, chartEntryIndexes[0], parser));
      }

      HeapUtils.removeMin(chartEntryIndexes, probsCopy, numChartEntries);
      numChartEntries--;
    }

    Collections.reverse(bestParses);
    return bestParses;
  }
  
  /**
   * Gets the highest-scoring {@code numParses} parses spanning
   * any subspan of {@code spanStart} to {@code spanEnd}.
   * 
   * @param spanStart
   * @param spanEnd
   * @param numParses
   * @param parser
   * @return
   */
  public List<CcgParse> decodeBestParsesForSubspan(int spanStart, int spanEnd, int numParses,
      CcgParser parser) {
    double[] probs = new double[numParses + 1];
    CcgParse[] parses = new CcgParse[numParses + 1];
    int heapSize = 0;
    
    for (int i = spanStart; i <= spanEnd; i++) {
      for (int j = i; j <= spanEnd; j++) {
        List<CcgParse> spanParses = decodeBestParsesForSpan(i, j, numParses, parser);
        
        for (CcgParse parse : spanParses) {
          HeapUtils.offer(parses, probs, heapSize, parse, parse.getSubtreeProbability());
          heapSize++;
          
          if (heapSize > numParses) {
            HeapUtils.removeMin(parses, probs, heapSize);
            heapSize--;
          }
        }
      }
    }

    List<CcgParse> bestParses = Lists.newArrayList();
    while (heapSize > 0) {
      bestParses.add(parses[0]);
      HeapUtils.removeMin(parses, probs, heapSize);
      heapSize--;
    }

    Collections.reverse(bestParses);
    return bestParses;
  }
  
  @Override
  public CcgParse decodeBestParse(CcgParser parser) {
    List<CcgParse> bestParses = decodeBestParsesForSpan(0, size() - 1, 1, parser);
    return Iterables.getFirst(bestParses, null);
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
  public IntMultimap getChartEntriesBySyntacticCategoryForSpan(int spanStart, int spanEnd) {
    return chartEntriesBySyntacticCategory[spanStart][spanEnd];
  }

  @Override
  public int getNumChartEntriesForSpan(int spanStart, int spanEnd) {
    return chartSizes[spanEnd + (numTerminals * spanStart)];
  }
  
  @Override
  public int getTotalNumChartEntries() {
    return totalChartSize;
  }

  @Override
  public void addChartEntryForSpan(ChartEntry entry, double probability, int spanStart,
      int spanEnd, DiscreteVariable syntaxVarType) {

    if (entryFilter != null) {
      probability *= Math.exp(entryFilter.apply(entry, spanStart, spanEnd, numTerminals, syntaxVarType));
    }

    if (probability != 0.0) {
      offerEntry(entry, probability, spanStart, spanEnd);
    }
  }

  @Override
  public void clearChartEntriesForSpan(int spanStart, int spanEnd) {
    totalChartSize -= chartSizes[spanEnd + (numTerminals * spanStart)];
    chartSizes[spanEnd + (numTerminals * spanStart)] = 0;
    chartEntriesBySyntacticCategory[spanStart][spanEnd] = null;

    // This part is unnecessary, but makes debugging easier.
    Arrays.fill(chart[spanStart][spanEnd], null);
  }

  @Override
  public void doneAddingChartEntriesForSpan(int spanStart, int spanEnd) {
    chartEntriesBySyntacticCategory[spanStart][spanEnd] = aggregateBySyntacticType(chart[spanStart][spanEnd],
        getNumChartEntriesForSpan(spanStart, spanEnd));
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
    totalChartSize++;

    if (chartSizes[spanEnd + (numTerminals * spanStart)] > beamSize) {
      HeapUtils.removeMin(chart[spanStart][spanEnd], probabilities[spanStart][spanEnd],
          chartSizes[spanEnd + (numTerminals * spanStart)]);
      chartSizes[spanEnd + (numTerminals * spanStart)]--;
      totalChartSize--;
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
