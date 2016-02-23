package com.jayantkrish.jklol.ccg;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.CcgLeftToRightChart;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.lexicon.AbstractCcgLexicon.SkipTrigger;
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
    parser.initializeChartTerminals(chart, sentence, false);
    List<String> lowercaseWords = sentence.getWordsLowercase();

    // Working heap for queuing parses to process next.
    KbestHeap<ShiftReduceStack> heap = new KbestHeap<ShiftReduceStack>(beamSize, new ShiftReduceStack[0]);
    heap.offer(ShiftReduceStack.empty(), 1.0);
    
    // Heap for finished parses.
    KbestHeap<ShiftReduceStack> finishedHeap = new KbestHeap<ShiftReduceStack>(beamSize,
        new ShiftReduceStack[0]);

    // Array of elements in the current beam.
    ShiftReduceStack[] currentBeam = new ShiftReduceStack[beamSize + 1];
    int currentBeamSize = 0;

    int numSteps = 0;
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
        skip(stack, chart, heap, parser, lowercaseWords);
        reduce(stack, chart, heap, parser, log);
        root(stack, chart, finishedHeap, parser);
      }
      
      shiftSkipLeft(numSteps, chart, heap, parser, lowercaseWords);
      numSteps++;
    }
    
    List<CcgParse> parses = Lists.newArrayList();
    while (finishedHeap.size() > 0) {
      ShiftReduceStack stack = finishedHeap.removeMin();
      parses.add(chart.decodeParseFromSpan(stack.spanStart, stack.spanEnd, stack.chartEntryIndex, parser));
    }
    Collections.reverse(parses);
    
    return parses;
  }
  
  public static final void root(ShiftReduceStack stack, CcgChart chart, KbestHeap<ShiftReduceStack> finishedHeap,
      CcgParser parser) { 
    if (stack.size == 1 && stack.spanEnd == chart.getWords().size() - 1 && !stack.includesRootProb) {
      // This parse spans all of the input words and has no remaining
      // reduce operations.
      
      // Try applying unary rules to the root.
      int startNumEntries = chart.getNumChartEntriesForSpan(stack.spanStart, stack.spanEnd);
      chart.addChartEntryForSpan(stack.entry, stack.totalProb, stack.spanStart, stack.spanEnd,
            parser.getSyntaxVarType());
      parser.applyUnaryRules(chart, stack.entry, stack.totalProb, stack.spanStart, stack.spanEnd);
      int midNumEntries = chart.getNumChartEntriesForSpan(stack.spanStart, stack.spanEnd);
      
      ChartEntry[] entries = chart.getChartEntriesForSpan(stack.spanStart, stack.spanEnd);
      double[] probs = chart.getChartEntryProbsForSpan(stack.spanStart, stack.spanEnd);
      for (int j = startNumEntries; j < midNumEntries; j++) {
        double rootProb = parser.scoreRootEntry(entries[j], chart);
        
        chart.addChartEntryForSpan(entries[j], probs[j] * rootProb, stack.spanStart, stack.spanEnd,
            parser.getSyntaxVarType());
      }
      
      int endNumEntries = chart.getNumChartEntriesForSpan(stack.spanStart, stack.spanEnd);
      entries = chart.getChartEntriesForSpan(stack.spanStart, stack.spanEnd);
      probs = chart.getChartEntryProbsForSpan(stack.spanStart, stack.spanEnd);
      for (int j = midNumEntries; j < endNumEntries; j++) {
        ShiftReduceStack nextStack = stack.previous.push(stack.spanStart, stack.spanEnd, j,
            entries[j], probs[j], true);
        
        finishedHeap.offer(nextStack, nextStack.totalProb);
      }
    }
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
        // tokens than this parse. (The second set of conditions
        // verifies that the chart entry doesn't skip any words.)
        ChartEntry e = entries[j];
        if (e.isTerminal() && e.getAdditionalInfo() == null &&
            e.getLeftSpanStart() == e.getRightSpanStart() && e.getLeftSpanEnd() == e.getRightSpanEnd()) {
          // System.out.println("SHIFT: " + curToken + "," + spanEnd + " " + entries[j].getHeadedSyntax());

          // Queue the shift action by adding it to the heap.
          ShiftReduceStack nextStack = stack.push(curToken, spanEnd, j, entries[j], probs[j], false);
          heap.offer(nextStack, nextStack.totalProb);
        }
      }
    }
  }
  
  public static final void shiftSkipLeft(int numTokensToSkip, CcgChart chart, KbestHeap<ShiftReduceStack> heap,
      CcgParser parser, List<String> lowercaseWords) {
    if (numTokensToSkip == 0 || numTokensToSkip >= chart.getWords().size() || !parser.canSkipWords()) {
      return;
    }
    
    double skipProb = 1.0;
    for (int i = 0; i < numTokensToSkip; i++) {
      skipProb *= parser.getWordSkipProbability(lowercaseWords.get(i));
    }

    ShiftReduceStack stack = ShiftReduceStack.empty();
    int spanStart = numTokensToSkip;
    int inputLength = chart.getWords().size();
    for (int spanEnd = spanStart; spanEnd < inputLength; spanEnd++) {
      ChartEntry[] entries = chart.getChartEntriesForSpan(spanStart, spanEnd);
      double[] probs = chart.getChartEntryProbsForSpan(spanStart, spanEnd);
      int numEntries = chart.getNumChartEntriesForSpan(spanStart, spanEnd);

      for (int j = 0; j < numEntries; j++) {
        // Some nonterminal entries may have been added to chart
        // by other parses in the beam that have processed more
        // tokens than this parse. (The second set of conditions
        // verifies that the chart entry doesn't skip any words.)
        ChartEntry e = entries[j];
        if (e.isTerminal() && e.getAdditionalInfo() == null &&
            e.getLeftSpanStart() == e.getRightSpanStart() && e.getLeftSpanEnd() == e.getRightSpanEnd()) {
          // System.out.println("SHIFT: " + curToken + "," + spanEnd + " " + entries[j].getHeadedSyntax());

          double prob = probs[j] * skipProb;
          int entryIndex = createSkipEntry(e, 0, spanEnd, prob, chart, parser);
          if (entryIndex != -1) {
            ChartEntry next = chart.getChartEntriesForSpan(0, spanEnd)[entryIndex];
          
            // Queue the shift action by adding it to the heap.
            ShiftReduceStack nextStack = stack.push(0, spanEnd, entryIndex, next, prob, false);
            heap.offer(nextStack, nextStack.totalProb);
          }
        }
      }
    }
  }
  
  public static final void skip(ShiftReduceStack stack, CcgChart chart, KbestHeap<ShiftReduceStack> heap,
      CcgParser parser, List<String> lowercaseWords) {
    // Perform word skipping action, if allowed by the parser.
    if (parser.canSkipWords() && stack.entry != null && stack.entry.isTerminal() &&
        stack.spanEnd < chart.getWords().size() - 1) {
      int nextToken = stack.spanEnd + 1;
      double skipProb = parser.getWordSkipProbability(lowercaseWords.get(nextToken));
    
      ChartEntry e = stack.entry;
      int spanStart = e.getLeftSpanStart();
      int spanEnd = nextToken;
      double nextProb = stack.entryProb * skipProb;
      
      int nextEntryIndex = createSkipEntry(e, spanStart, spanEnd, nextProb, chart, parser);
      if (nextEntryIndex != -1) {
        ChartEntry next = chart.getChartEntriesForSpan(spanStart, spanEnd)[nextEntryIndex];

        ShiftReduceStack nextStack = new ShiftReduceStack(spanStart, spanEnd, nextEntryIndex, next,
            nextProb, false, stack.previous);
        heap.offer(nextStack, nextStack.totalProb);
      }
    }
  }
  
  private static final int createSkipEntry(ChartEntry e, int newSpanStart, int newSpanEnd, double entryProb,
      CcgChart chart, CcgParser parser) {
    int triggerSpanStart = e.getRightSpanStart();
    int triggerSpanEnd = e.getRightSpanEnd();
    Object oldTrigger = e.getLexiconTrigger();
    Object trigger = null;
    if (oldTrigger instanceof SkipTrigger) {
      trigger = oldTrigger;
    } else {
      trigger = new SkipTrigger(trigger, triggerSpanStart, triggerSpanEnd);
    }

    // TODO: handle the additionalInfo for GroundedParser
    ChartEntry next = new ChartEntry(e.getHeadedSyntax(), e.getSyntaxUniqueVars(), e.getHeadVariable(),
        e.getLexiconEntry(), trigger, e.getLexiconIndex(), e.getRootUnaryRule(), e.getAssignmentVarIndex(),
        e.getAssignments(), e.getUnfilledDependencyVarIndex(), e.getUnfilledDependencies(), e.getDependencies(),
        newSpanStart, newSpanEnd, triggerSpanStart, triggerSpanEnd);

    int initialNumEntries = chart.getNumChartEntriesForSpan(newSpanStart, newSpanEnd);
    chart.addChartEntryForSpan(next, entryProb, newSpanStart, newSpanEnd, parser.getSyntaxVarType());
    int finalNumEntries = chart.getNumChartEntriesForSpan(newSpanStart, newSpanEnd);
    
    if (finalNumEntries > initialNumEntries) {
      return finalNumEntries - 1;
    } else {
      return -1;
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
        ShiftReduceStack toQueue = prev.previous.push(prev.spanStart, stack.spanEnd, j,
            chartEntries[j], chartProbs[j], false);
        heap.offer(toQueue, toQueue.totalProb);
      }
    }
  }
}
