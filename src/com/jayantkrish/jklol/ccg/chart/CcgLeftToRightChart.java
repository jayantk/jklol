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
import com.jayantkrish.jklol.util.HeapUtils;
import com.jayantkrish.jklol.util.IntMultimap;

public class CcgLeftToRightChart extends AbstractCcgChart {

  private final int numTerminals;

  private final ChartEntry[][][] chart;
  private final double[][][] probabilities;
  private final int[][] chartSizes;

  private int totalChartSize;
  
  private static final int NUM_INITIAL_SPAN_ENTRIES = 200;

  /**
   * Creates a CCG chart for storing the current state of a beam
   * search trying to parse {@code terminals}.
   * 
   * @param terminals
   * @param posTags
   */
  public CcgLeftToRightChart(AnnotatedSentence sentence, int maxChartSize) {
    super(sentence, maxChartSize);

    numTerminals = sentence.size();
    this.chart = new ChartEntry[numTerminals][numTerminals][NUM_INITIAL_SPAN_ENTRIES];
    this.probabilities = new double[numTerminals][numTerminals][NUM_INITIAL_SPAN_ENTRIES];
    this.chartSizes = new int[numTerminals][numTerminals];

    for (int spanStart = 0; spanStart < numTerminals; spanStart++) {
      for (int spanEnd = 0; spanEnd < numTerminals; spanEnd++) {
        chart[spanStart][spanEnd] = new ChartEntry[NUM_INITIAL_SPAN_ENTRIES];
        probabilities[spanStart][spanEnd] = new double[NUM_INITIAL_SPAN_ENTRIES];
        chartSizes[spanStart][spanEnd] = 0;
      }
    }

    this.totalChartSize = 0;
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
    double[] probs = probabilities[spanStart][spanEnd];
    double[] probabilityHeap = new double[probs.length];
    Integer[] chartEntryIndexes = new Integer[probabilities[spanStart][spanEnd].length];
    int numChartEntries = getNumChartEntriesForSpan(spanStart, spanEnd);
    int heapSize = 0;

    for (int i = 0; i < numChartEntries; i++) {
      HeapUtils.offer(chartEntryIndexes, probabilityHeap, heapSize, i, probs[i]);
      heapSize++;
    }

    // Heaps are min-heaps, so we throw away the initial entries.
    // Then the remaining entries are the best parses.
    List<CcgParse> bestParses = Lists.newArrayList();
    
    while (heapSize > 0) {
      if (heapSize <= numParses) {
        bestParses.add(decodeParseFromSpan(spanStart, spanEnd, chartEntryIndexes[0], parser));
      }

      HeapUtils.removeMin(chartEntryIndexes, probabilityHeap, heapSize);
      heapSize--;
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
    return aggregateBySyntacticType(chart[spanStart][spanEnd], getNumChartEntriesForSpan(spanStart, spanEnd));
  }

  @Override
  public int getNumChartEntriesForSpan(int spanStart, int spanEnd) {
    return chartSizes[spanStart][spanEnd];
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
    totalChartSize -= chartSizes[spanStart][spanEnd];
    chartSizes[spanStart][spanEnd] = 0;
    // This second part is unnecessary, but makes debugging easier.
    Arrays.fill(chart[spanStart][spanEnd], null);
  }

  @Override
  public void doneAddingChartEntriesForSpan(int spanStart, int spanEnd) {
  }

  /**
   * Adds a chart entry to the heap for {@code spanStart} to
   * {@code spanEnd}. This operation preserves existing chart
   * entry indexes and automatically resizes any arrays to fit
   * the new entry.
   */
  private final void offerEntry(ChartEntry entry, double probability, int spanStart, int spanEnd) {
    int nextEntryIndex = chartSizes[spanStart][spanEnd];
    if (nextEntryIndex == chart[spanStart][spanEnd].length) {
      // Resize the arrays to fit the new entry.
      ChartEntry[] spanChart = chart[spanStart][spanEnd];
      double[] spanProbs = probabilities[spanStart][spanEnd];
      chart[spanStart][spanEnd] = Arrays.copyOf(spanChart, spanChart.length * 2);
      probabilities[spanStart][spanEnd] = Arrays.copyOf(spanProbs, spanChart.length * 2);
    }

    chart[spanStart][spanEnd][nextEntryIndex] = entry;
    probabilities[spanStart][spanEnd][nextEntryIndex] = probability;
    chartSizes[spanStart][spanEnd]++;
    totalChartSize++;
  }

  public static ChartEntry[] copyChartEntryArray(ChartEntry[] entries, int numEntries) {
    ChartEntry[] returnValue = new ChartEntry[numEntries];
    for (int i = 0; i < numEntries; i++) {
      returnValue[i] = entries[i];
    }
    return returnValue;
  }
}
