package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.jayantkrish.jklol.ccg.chart.CcgLeftToRightChart;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.HeapUtils;
import com.jayantkrish.jklol.util.IntMultimap;

/**
 * Shift-reduce CCG parsing algorithm.
 * 
 * @author jayantk
 */
public class CcgShiftReduceInference implements CcgInference {
  
  private final int beamSize;
  
  public CcgShiftReduceInference(int beamSize) {
    this.beamSize = beamSize;
  }
  
  @Override
  public CcgParse getBestParse(CcgParser parser, AnnotatedSentence sentence,
      ChartCost chartFilter, LogFunction log) {
    List<CcgParse> parses = beamSearch(parser, sentence, chartFilter, log);

    if (parses.size() > 0) {
      return parses.get(0);
    } else {
      return null;
    }
  }

  @Override
  public List<CcgParse> beamSearch(CcgParser parser, AnnotatedSentence sentence,
      ChartCost chartFilter, LogFunction log) {
    CcgLeftToRightChart chart = new CcgLeftToRightChart(sentence, Integer.MAX_VALUE);
    parser.initializeChart(chart, sentence, chartFilter);
    parser.initializeChartTerminals(chart, sentence);
    
    int inputLength = sentence.getWords().size();

    // Working heap for queuing parses to process next.
    double[] heapValues = new double[beamSize + 1];
    Stack[] heapKeys = new Stack[beamSize + 1];
    int heapSize = 0;
    heapSize = queue(Stack.empty(), heapKeys, heapValues, heapSize);

    // Array of elements in the current beam.
    Stack[] currentBeam = new Stack[beamSize + 1];
    int currentBeamSize = 0;

    while (heapSize > 0) {
      // Copy the heap to the current beam.
      for (int i = 0; i < heapSize; i++) {
        currentBeam[i] = heapKeys[i];
      }
      
      // System.out.println();
      // System.out.println("LOOP: " + heapSize);

      // Empty out the heap.
      currentBeamSize = heapSize;
      heapSize = 0;
      
      for (int i = 0; i < currentBeamSize; i++) {
        Stack stack = currentBeam[i];
        int curToken = stack.spanEnd + 1;
        // System.out.println("Processing " + stack);

        // Perform SHIFT actions.
        // The possible shift actions are the chart entries at
        // the span (curToken, curToken) to the span (curToken, inputLength - 1).
        // There is more than one possible span because lexicons may
        // contain entries spanning multiple tokens.
        // Also note that CcgChart uses an inclusive end index.
        for (int spanEnd = curToken; spanEnd < inputLength; spanEnd++) {
          ChartEntry[] entries = chart.getChartEntriesForSpan(curToken, spanEnd);
          double[] probs = chart.getChartEntryProbsForSpan(curToken, spanEnd);
          int numEntries = chart.getNumChartEntriesForSpan(curToken, spanEnd);

          for (int j = 0; j < numEntries; j++) {
            // Some nonterminal entries may have been added to chart
            // by other parses in the beam that have processed more
            // tokens than this parse.
            if (entries[j].isTerminal()) {
              // System.out.println("SHIFT: " + curToken + "," + spanEnd);
              
              // Queue the shift action by adding it to the heap.
              Stack nextStack = stack.push(curToken, spanEnd, j, entries[j], probs[j]);
              heapSize = queue(nextStack, heapKeys, heapValues, heapSize);
            }
          }
        }

        // Perform REDUCE actions.
        if (stack.size > 1) {
          Stack prev = stack.previous;

          ChartEntry[] prevEntryArray = chart.getChartEntriesForSpan(prev.spanStart, prev.spanEnd);
          double[] prevEntryProbArray = chart.getChartEntryProbsForSpan(prev.spanStart, prev.spanEnd);
          IntMultimap prevTypes = IntMultimap.createFromUnsortedArrays(
              new int[] {prev.entry.getHeadedSyntax()}, new int[] {prev.chartEntryIndex}, 0);
          
          ChartEntry[] stackEntryArray = chart.getChartEntriesForSpan(stack.spanStart, stack.spanEnd);
          double[] stackEntryProbArray = chart.getChartEntryProbsForSpan(stack.spanStart, stack.spanEnd);
          IntMultimap stackTypes = IntMultimap.createFromUnsortedArrays(
              new int[] {stack.entry.getHeadedSyntax()}, new int[] {stack.chartEntryIndex}, 0);
          
          int startNumEntries = chart.getNumChartEntriesForSpan(prev.spanStart, stack.spanEnd);
          parser.applySearchMoves(chart, prev.spanStart, prev.spanEnd, stack.spanStart, stack.spanEnd,
              prevEntryArray, prevEntryProbArray, prevTypes, stackEntryArray, stackEntryProbArray, stackTypes);
          int endNumEntries = chart.getNumChartEntriesForSpan(prev.spanStart, stack.spanEnd);

          ChartEntry[] chartEntries = chart.getChartEntriesForSpan(prev.spanStart, stack.spanEnd);
          double[] chartProbs = chart.getChartEntryProbsForSpan(prev.spanStart, stack.spanEnd);
          for (int j = startNumEntries; j < endNumEntries; j++) {
            // System.out.println("REDUCE: " + stack + " --> " + parser.getSyntaxVarType().getValue(chartEntries[j].getHeadedSyntax()));
            Stack toQueue = prev.previous.push(prev.spanStart, stack.spanEnd, j, chartEntries[j], chartProbs[j]);
            heapSize = queue(toQueue, heapKeys, heapValues, heapSize);
          }
        }
      }
    }

    parser.reweightRootEntries(chart);
    // System.out.println("NUM ROOT: " + chart.getNumChartEntriesForSpan(0, chart.size() - 1));
    
    return chart.decodeBestParsesForSpan(0, chart.size() - 1, beamSize, parser);
  }
  
  private final int queue(Stack toQueue, Stack[] heapKeys, double[] heapValues, int heapSize) {
    HeapUtils.offer(heapKeys, heapValues, heapSize, toQueue, toQueue.totalProb);
    heapSize++;
    if (heapSize > beamSize) {
      HeapUtils.removeMin(heapKeys, heapValues, heapSize);
      heapSize--;
    }
    return heapSize;
  }
  
  private static class Stack {
    public final int spanStart;
    public final int spanEnd;
    public final int chartEntryIndex;
    public final ChartEntry entry;
    public final double entryProb;
    
    public final Stack previous;

    public final int size;
    public final double totalProb;

    public Stack(int spanStart, int spanEnd, int chartEntryIndex, ChartEntry entry,
        double entryProb, Stack previous) {
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

    public static Stack empty() {
      return new Stack(-1, -1, -1, null, 1.0, null);
    }

    public Stack push(int spanStart, int spanEnd, int chartEntryIndex, ChartEntry entry, double prob) {
      return new Stack(spanStart, spanEnd, chartEntryIndex, entry, prob, this);
    }
    
    @Override
    public String toString() {
      String prevString = previous == null ? "" : previous.toString();
      return spanStart + "," + spanEnd + " : " + prevString;
    }
  }
}
