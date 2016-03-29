package com.jayantkrish.jklol.ccg;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.CcgLeftToRightChart;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.IntMultimap;
import com.jayantkrish.jklol.util.KbestQueue;
import com.jayantkrish.jklol.util.SearchQueue;
import com.jayantkrish.jklol.util.SegregatedKbestQueue;

/**
 * Shift-reduce CCG parsing algorithm.
 * 
 * @author jayantk
 */
public class CcgShiftReduceInference implements CcgInference {
  
  private final int beamSize;
  private final int maxStackSize;

  public CcgShiftReduceInference(int beamSize, int maxStackSize) {
    this.beamSize = beamSize;
    this.maxStackSize = maxStackSize;
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
    int curMaxStackSize = maxStackSize > -1 ? maxStackSize : sentence.getWords().size() + 1;
    SearchQueue<ShiftReduceStack> heap = new SegregatedKbestQueue<ShiftReduceStack>(
        curMaxStackSize, beamSize, new StackSize(curMaxStackSize), new ShiftReduceStack[0]);
    heap.offer(ShiftReduceStack.empty(), 1.0);
    
    // Heap for finished parses.
    KbestQueue<ShiftReduceStack> finishedHeap = new KbestQueue<ShiftReduceStack>(beamSize,
        new ShiftReduceStack[0]);
    
    // Temporary heaps
    SearchQueue<ShiftReduceStack> tempHeap1 = new KbestQueue<ShiftReduceStack>(beamSize,
        new ShiftReduceStack[0]);  
    SearchQueue<ShiftReduceStack> tempHeap2 = new KbestQueue<ShiftReduceStack>(beamSize,
        new ShiftReduceStack[0]);

    // Array of elements in the current beam.
    ShiftReduceStack[] currentBeam = new ShiftReduceStack[beamSize * curMaxStackSize];
    int currentBeamSize = 0;
    
    // System.out.println(sentence.getWords());

    int numSteps = 0;
    while (heap.size() > 0 || numSteps < chart.getWords().size()) {
      // Copy the heap to the current beam.
      ShiftReduceStack[] keys = heap.getItems();
      // System.out.println("LOOP " + numSteps + ":" + heap.size());
      for (int i = 0; i < heap.size(); i++) {
        currentBeam[i] = keys[i];
        // System.out.println(currentBeam[i]);
      }
      
      // Empty the heap.
      currentBeamSize = heap.size();
      heap.clear();
      
      // System.out.println();
      // System.out.println("LOOP: " + currentBeamSize);
      
      for (int i = 0; i < currentBeamSize; i++) {
        ShiftReduceStack stack = currentBeam[i];
        // System.out.println("Processing " + stack);
        shiftReduce(stack, chart, heap, tempHeap1, tempHeap2, parser, log);

        skip(stack, chart, heap, parser, lowercaseWords);
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
  
  public static final void root(ShiftReduceStack stack, CcgChart chart, SearchQueue<ShiftReduceStack> finishedHeap,
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
  
  public static final void shift(ShiftReduceStack stack, CcgChart chart, SearchQueue<ShiftReduceStack> heap) {
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
  
  public static final void shiftSkipLeft(int numTokensToSkip, CcgChart chart, SearchQueue<ShiftReduceStack> heap,
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
          int entryIndex = createSkipEntryTerminal(e, 0, spanEnd, prob, chart, parser);
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
  
  public static final void skip(ShiftReduceStack stack, CcgChart chart, SearchQueue<ShiftReduceStack> heap,
      CcgParser parser, List<String> lowercaseWords) {
    // Perform word skipping action, if allowed by the parser.
    if (parser.canSkipWords() && stack.entry != null
        && stack.spanEnd < chart.getWords().size() - 1) {
      int nextToken = stack.spanEnd + 1;
      double skipProb = parser.getWordSkipProbability(lowercaseWords.get(nextToken));

      int nextEntryIndex = createSkipEntryNonterminal(stack.spanStart, stack.spanEnd,
          stack.chartEntryIndex, nextToken, skipProb, chart, parser);
      
      if (nextEntryIndex != -1) {
        ChartEntry next = chart.getChartEntriesForSpan(stack.spanStart, nextToken)[nextEntryIndex];
        double nextProb = chart.getChartEntryProbsForSpan(stack.spanStart, nextToken)[nextEntryIndex];

        ShiftReduceStack nextStack = new ShiftReduceStack(stack.spanStart, nextToken, nextEntryIndex,
            next, nextProb, false, stack.previous);
        heap.offer(nextStack, nextStack.totalProb);
      }
    }
  }

  private static final int createSkipEntryNonterminal(int entrySpanStart, int entrySpanEnd, int entryIndex,
      int newSpanEnd, double skipProb, CcgChart chart, CcgParser parser) {
    ChartEntry e = chart.getChartEntriesForSpan(entrySpanStart, entrySpanEnd)[entryIndex];
    double entryProb = chart.getChartEntryProbsForSpan(entrySpanStart, entrySpanEnd)[entryIndex];
    
    if (e.isTerminal()) {
      return createSkipEntryTerminal(e, e.getLeftSpanStart(), newSpanEnd, entryProb * skipProb, chart, parser);
    } else {
      int rightIndex = e.getRightChartIndex();
      int rightSpanStart = e.getRightSpanStart();
      int rightSpanEnd = e.getRightSpanEnd();
      
      int newRightIndex = createSkipEntryNonterminal(rightSpanStart, rightSpanEnd, rightIndex,
          newSpanEnd, skipProb, chart, parser);
      
      if (newRightIndex != -1) {
        ChartEntry next = e.replaceRight(rightSpanStart, newSpanEnd, newRightIndex);
        
        int nextSpanStart = next.getLeftSpanStart();
        int nextSpanEnd = next.getRightSpanEnd();
        int initialNumEntries = chart.getNumChartEntriesForSpan(nextSpanStart, nextSpanEnd);
        chart.addChartEntryForSpan(next, entryProb * skipProb, nextSpanStart, nextSpanEnd,
            parser.getSyntaxVarType());
        int finalNumEntries = chart.getNumChartEntriesForSpan(nextSpanStart, newSpanEnd);

        if (finalNumEntries > initialNumEntries) {
          return finalNumEntries - 1;
        } else {
          return -1;
        }
      } else {
        return -1;
      }
    }
  }
  
  private static final int createSkipEntryTerminal(ChartEntry e, int newSpanStart, int newSpanEnd, double entryProb,
      CcgChart chart, CcgParser parser) {
    int triggerSpanStart = e.getRightSpanStart();
    int triggerSpanEnd = e.getRightSpanEnd();
    Object trigger = e.getLexiconTrigger();

    ChartEntry next = new ChartEntry(e.getHeadedSyntax(), e.getSyntaxUniqueVars(), e.getHeadVariable(),
        e.getLexiconEntry(), trigger, e.getLexiconIndex(), e.getRootUnaryRule(), e.getAssignmentVarIndex(),
        e.getAssignments(), e.getUnfilledDependencyVarIndex(), e.getUnfilledDependencies(), e.getDependencies(),
        newSpanStart, newSpanEnd, triggerSpanStart, triggerSpanEnd);
    
    if (e.getAdditionalInfo() != null) {
      next = next.addAdditionalInfo(e.getAdditionalInfo());
    }

    int initialNumEntries = chart.getNumChartEntriesForSpan(newSpanStart, newSpanEnd);
    chart.addChartEntryForSpan(next, entryProb, newSpanStart, newSpanEnd, parser.getSyntaxVarType());
    int finalNumEntries = chart.getNumChartEntriesForSpan(newSpanStart, newSpanEnd);
    
    if (finalNumEntries > initialNumEntries) {
      return finalNumEntries - 1;
    } else {
      return -1;
    }
  }
  
  public static final void reduce(ShiftReduceStack stack, CcgChart chart, SearchQueue<ShiftReduceStack> heap,
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
  
  public static final void shiftReduce(ShiftReduceStack stack, CcgChart chart, SearchQueue<ShiftReduceStack> heap,
      SearchQueue<ShiftReduceStack> tempHeap1, SearchQueue<ShiftReduceStack> tempHeap2, CcgParser parser, LogFunction log) {
    tempHeap1.clear();
    shift(stack, chart, tempHeap1);
    
    SearchQueue<ShiftReduceStack> curTempHeap = tempHeap1;
    SearchQueue<ShiftReduceStack> nextTempHeap = tempHeap2;
    while (curTempHeap.size() > 0) {
      nextTempHeap.clear();

      ShiftReduceStack[] keys = curTempHeap.getItems();
      for (int i = 0; i < curTempHeap.size(); i++) {
        heap.offer(keys[i], keys[i].totalProb);
        
        reduce(keys[i], chart, nextTempHeap, parser, log);
      }
      
      SearchQueue<ShiftReduceStack> swap = curTempHeap;
      curTempHeap = nextTempHeap;
      nextTempHeap = swap;
    }
  }
  
  private static final class StackSize implements Function<ShiftReduceStack, Integer> {
    private final int maxStackSize;
    public StackSize(int maxStackSize) {
      this.maxStackSize = maxStackSize;
    }

    @Override
    public Integer apply(ShiftReduceStack stack) {
      int size = stack.size;
      if (size < maxStackSize) {
        return size;
      } else {
        return -1;
      }
    }
  }
}
