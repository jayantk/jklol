package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.CcgLeftToRightChart;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.IntMultimap;
import com.jayantkrish.jklol.util.KbestHeap;

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

    // Working heap for queuing parses to process next.
    KbestHeap<ShiftReduceStack> heap = new KbestHeap<ShiftReduceStack>(beamSize, new ShiftReduceStack[0]);
    heap.offer(ShiftReduceStack.empty(), 1.0);

    // Array of elements in the current beam.
    ShiftReduceStack[] currentBeam = new ShiftReduceStack[beamSize + 1];
    int currentBeamSize = 0;

    while (heap.size() > 0) {
      // Copy the heap to the current beam.
      ShiftReduceStack[] keys = heap.getKeys();
      for (int i = 0; i < heap.size(); i++) {
        currentBeam[i] = keys[i];
      }
      
      // System.out.println();
      // System.out.println("LOOP: " + heapSize);

      // Empty the heap.
      currentBeamSize = heap.size();
      heap.clear();
      
      for (int i = 0; i < currentBeamSize; i++) {
        ShiftReduceStack stack = currentBeam[i];
        // System.out.println("Processing " + stack);
        shift(stack, chart, heap);
        reduce(stack, chart, heap, parser, log);
      }
    }

    parser.reweightRootEntries(chart);
    // System.out.println("NUM ROOT: " + chart.getNumChartEntriesForSpan(0, chart.size() - 1));
    
    return chart.decodeBestParsesForSpan(0, chart.size() - 1, beamSize, parser);
  }
  
  public static final void shift(ShiftReduceStack stack, CcgChart chart, KbestHeap<ShiftReduceStack> heap) {
    // Perform SHIFT actions.
    // The possible shift actions are the chart entries at
    // the span (curToken, curToken) to the span (curToken, inputLength - 1).
    // There is more than one possible span because lexicons may
    // contain entries spanning multiple tokens.
    // Also note that CcgChart uses an inclusive end index.
    int curToken = stack.spanEnd + 1;
    int inputLength = chart.getWords().size();
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
          ShiftReduceStack nextStack = stack.push(curToken, spanEnd, j, entries[j], probs[j]);
          heap.offer(nextStack, nextStack.totalProb);
        }
      }
    }
  }
  
  public static final void reduce(ShiftReduceStack stack, CcgChart chart, KbestHeap<ShiftReduceStack> heap,
      CcgParser parser, LogFunction log) {
    // Perform REDUCE actions.
    if (stack.size > 1) {
      ShiftReduceStack prev = stack.previous;

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
          prevEntryArray, prevEntryProbArray, prevTypes, stackEntryArray, stackEntryProbArray, stackTypes,
          log);
      int endNumEntries = chart.getNumChartEntriesForSpan(prev.spanStart, stack.spanEnd);

      ChartEntry[] chartEntries = chart.getChartEntriesForSpan(prev.spanStart, stack.spanEnd);
      double[] chartProbs = chart.getChartEntryProbsForSpan(prev.spanStart, stack.spanEnd);
      for (int j = startNumEntries; j < endNumEntries; j++) {
        // System.out.println("REDUCE: " + stack + " --> " + parser.getSyntaxVarType().getValue(chartEntries[j].getHeadedSyntax()));
        ShiftReduceStack toQueue = prev.previous.push(prev.spanStart, stack.spanEnd, j, chartEntries[j], chartProbs[j]);
        heap.offer(toQueue, toQueue.totalProb);
      }
    }
  }
}
