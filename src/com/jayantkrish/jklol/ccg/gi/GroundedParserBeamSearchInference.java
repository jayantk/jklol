package com.jayantkrish.jklol.ccg.gi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgShiftReduceInference;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntryInfo;
import com.jayantkrish.jklol.ccg.ShiftReduceStack;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.CcgLeftToRightChart;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.KbestQueue;
import com.jayantkrish.jklol.util.SearchQueue;
import com.jayantkrish.jklol.util.SegregatedKbestQueue;

public class GroundedParserBeamSearchInference extends AbstractGroundedParserInference {
  private final int beamSize;
  private final int maxStackSize;
  
  private final int TEMP_HEAP_MAX_SIZE=100000;
  
  public GroundedParserBeamSearchInference(int beamSize, int maxStackSize) {
    this.beamSize = beamSize;
    this.maxStackSize = maxStackSize;
  }

  @Override
  public List<GroundedCcgParse> beamSearch(GroundedParser parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, Predicate<State> evalFilter,
      LogFunction log) {
    CcgLeftToRightChart chart = new CcgLeftToRightChart(sentence, Integer.MAX_VALUE);
    parser.getCcgParser().initializeChart(chart, sentence, chartFilter);
    parser.getCcgParser().initializeChartTerminals(chart, sentence, false);
    List<String> lowercaseWords = sentence.getWordsLowercase();

    // Working heap for queuing parses to process next.
    int curMaxStackSize = maxStackSize > -1 ? maxStackSize : sentence.getWords().size() + 1;
    SearchQueue<State> heap = new SegregatedKbestQueue<State>(
        curMaxStackSize, beamSize, new StateSize(curMaxStackSize), new State[0]);
    State startState = new State(ShiftReduceStack.empty(), initialDiagram, parser.getEval().getEnvironment(), null);
    heap.offer(startState, 1.0);

    // Heap for finished parses.
    KbestQueue<State> finishedHeap = new KbestQueue<State>(beamSize, new State[0]);

    // Temporary heaps for interfacing with CcgShiftReduceInference.
    KbestQueue<ShiftReduceStack> tempHeap = new KbestQueue<ShiftReduceStack>(TEMP_HEAP_MAX_SIZE,
        new ShiftReduceStack[0]);
    KbestQueue<ShiftReduceStack> tempHeap2 = new KbestQueue<ShiftReduceStack>(beamSize,
        new ShiftReduceStack[0]);
    KbestQueue<ShiftReduceStack> tempHeap3 = new KbestQueue<ShiftReduceStack>(beamSize,
        new ShiftReduceStack[0]);

    // Temporary list of evaluation results for interfacing with IncrementalEval
    List<IncEvalState> tempEvalResults = Lists.newArrayList();

    // Array of elements in the current beam.
    State[] currentBeam = new State[beamSize * curMaxStackSize];
    int currentBeamSize = 0;

    int numSteps = 0;
    while (heap.size() > 0 || numSteps < chart.getWords().size()) {
      // Copy the heap to the current beam.
      State[] keys = heap.getItems();
      for (int i = 0; i < heap.size(); i++) {
        currentBeam[i] = keys[i];
      }

      // Empty the heap.
      currentBeamSize = heap.size();
      System.out.println(currentBeamSize);
      heap.clear();

      for (int i = 0; i < currentBeamSize; i++) {
        State state = currentBeam[i];

        // Debugging code.
        /*
        String curSyntax = "START";
        if (state.stack.size > 0) {
          curSyntax = parser.getSyntaxVarType().getValue(state.stack.entry.getHeadedSyntax()).toString();
        }
        int curSpanStart = state.stack.spanStart;
        int curSpanEnd = state.stack.spanEnd;
         */

        if (state.evalResult != null) {
          log.startTimer("grounded_parser/evaluate_continuation");
          evaluateContinuation(state, tempEvalResults, heap,
              finishedHeap, chart, parser, evalFilter, log);
          log.stopTimer("grounded_parser/evaluate_continuation");
        } else {
          log.startTimer("grounded_parser/shift_reduce");
          tempHeap.clear();

          CcgShiftReduceInference.skip(state.stack, chart, tempHeap, parser.getCcgParser(),
              lowercaseWords);
          // CcgShiftReduceInference.shift(state.stack, chart, tempHeap);
          // CcgShiftReduceInference.reduce(state.stack, chart, tempHeap, parser, log);
          CcgShiftReduceInference.shiftReduce(state.stack, chart, tempHeap, tempHeap2, tempHeap3,
              parser.getCcgParser(), log);
          // Applying unary rules at the root does not end parsing in this model,
          // as we may need to evaluate the logical form produced at the root.
          CcgShiftReduceInference.root(state.stack, chart, tempHeap, parser.getCcgParser());

          // Ensure that we didn't discard any candidate parses due to the
          // capped temporary heap size.
          Preconditions.checkState(tempHeap.size() < TEMP_HEAP_MAX_SIZE);
          offerParseStates(state, tempHeap, heap, finishedHeap, tempEvalResults, chart, parser,
              evalFilter, log);
          // System.out.println("shift/reducing: " + curSyntax + " " + curSpanStart + "," + curSpanEnd);
          log.stopTimer("grounded_parser/shift_reduce");
        }
      }

      tempHeap.clear();
      CcgShiftReduceInference.shiftSkipLeft(numSteps, chart, tempHeap, parser.getCcgParser(),
          lowercaseWords);
      offerParseStates(startState, tempHeap, heap, finishedHeap, tempEvalResults, chart,
          parser, evalFilter, log);
      numSteps++;
    }

    List<GroundedCcgParse> parses = Lists.newArrayList();
    while (finishedHeap.size() > 0) {
      State state = finishedHeap.removeMin();
      ShiftReduceStack stack = state.stack;
      GroundedCcgParse parse = decodeParseFromSpan(stack.spanStart, stack.spanEnd,
          stack.chartEntryIndex, chart, parser.getCcgParser());
      parses.add(parse.addDiagram(state.diagram));
    }
    Collections.reverse(parses);

    return parses;
  }

  private void offerParseStates(State state, SearchQueue<ShiftReduceStack> tempHeap,
      SearchQueue<State> heap, SearchQueue<State> finishedHeap, List<IncEvalState> tempEvalResults,
      CcgChart chart, GroundedParser parser, Predicate<State> evalFilter, LogFunction log) {
    ShiftReduceStack[] tempHeapKeys = tempHeap.getItems();
    for (int j = 0; j < tempHeap.size(); j++) {
      ShiftReduceStack result = tempHeapKeys[j];

      Object continuation = null;
      Environment continuationEnv = null;
      HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) parser.getCcgParser().getSyntaxVarType()
          .getValue(result.entry.getHeadedSyntax());

      // System.out.println("  : " + result.spanStart + "," + result.spanEnd + " " + syntax);

      // Try evaluating entries that have the appropriate syntactic category
      // and that have not already been evaluated.
      if (result.entry.getAdditionalInfo() == null && parser.getEval().isEvaluatable(syntax)) {
        log.startTimer("grounded_parser/shift_reduce/initialize_continuation");
        GroundedCcgParse parse = decodeParseFromSpan(result.spanStart, result.spanEnd,
            result.chartEntryIndex, chart, parser.getCcgParser());

        continuationEnv = Environment.extend(state.env);
        continuation = parser.getEval().parseToContinuation(parse, continuationEnv);
        log.stopTimer("grounded_parser/shift_reduce/initialize_continuation");
      }

      if (continuation != null) {
        // Do one-step lookahead on continuation evaluation. This 
        // should reduce the necessary size of the beam.
        log.startTimer("grounded_parser/shift_reduce/evaluate_continuation");
        IncEvalState r = new IncEvalState(continuation, continuationEnv,
            null, state.diagram, 1.0, null);
        State next = new State(result, state.diagram, null, r);
        evaluateContinuation(next, tempEvalResults, heap,
            finishedHeap, chart, parser, evalFilter, log);
        log.stopTimer("grounded_parser/shift_reduce/evaluate_continuation");
      } else {
        State next = new State(result, state.diagram, state.env, null);
        offer(heap, finishedHeap, next, evalFilter);
      }
    }
  }

  private void evaluateContinuation(State state, List<IncEvalState> tempEvalResults,
      SearchQueue<State> heap, SearchQueue<State> finishedHeap, CcgChart chart,
      GroundedParser parser, Predicate<State> evalFilter, LogFunction log) {
    IncEvalState next = state.evalResult;
    while (next != null) {
      tempEvalResults.clear();
      parser.getEval().evaluateContinuation(next, tempEvalResults, log);

      // TODO: make the lookahead work with the filter as well.
      // Do lookahead for any state that is deterministically evaluated.
      if (tempEvalResults.size() == 1) {
        IncEvalState nextInc = tempEvalResults.get(0);
        if (nextInc.getContinuation() != null) {
          next = nextInc;
        } else {
          next = null;
          queueEvalState(nextInc, state.stack, heap, finishedHeap, chart, parser, evalFilter);
        }
      } else {
        for (IncEvalState result : tempEvalResults) {
          queueEvalState(result, state.stack, heap, finishedHeap, chart, parser, evalFilter);
        }
        next = null;
      }
    }
  }

  /**
   * Queues a search state containing the result of an evaluation. 
   * 
   * @param evalResult
   * @param cur
   * @param heap
   * @param finishedHeap
   * @param chart
   * @param evalFilter
   */
  private void queueEvalState(IncEvalState evalResult, ShiftReduceStack cur,
      SearchQueue<State> heap, SearchQueue<State> finishedHeap,
      CcgChart chart, GroundedParser parser, Predicate<State> evalFilter) {
    if (evalResult.getContinuation() == null) {
      // Evaluation has finished (for now) and the search must switch back
      // to parsing. Create a new entry on the CCG chart representing the
      // the post-evaluation parser state; this entry is required in order
      // to reference the result of evaluation in the future.
      int spanStart = cur.spanStart;
      int spanEnd = cur.spanEnd;

      ChartEntry entry = cur.entry.addAdditionalInfo(evalResult);
      double entryProb = cur.entryProb * evalResult.getProb();

      int entryIndex = chart.getNumChartEntriesForSpan(spanStart, spanEnd);
      chart.addChartEntryForSpan(entry, entryProb, spanStart, spanEnd,
          parser.getCcgParser().getSyntaxVarType());

      ShiftReduceStack newStack = cur.previous.push(cur.spanStart, cur.spanEnd, entryIndex,
          entry, entryProb, cur.includesRootProb);

      State next = new State(newStack, evalResult.getDiagram(), evalResult.getEnvironment(), null);
      offer(heap, finishedHeap, next, evalFilter);
    } else {
      // Evaluation is still in progress. 
      State next = new State(cur, null, null, evalResult);
      offer(heap, finishedHeap, next, evalFilter);
    }
  }

  private static final void offer(SearchQueue<State> heap, SearchQueue<State> finishedHeap,
      State state, Predicate<State> filter) {
    if (filter == null || filter.apply(state)) {
      if (state.evalResult == null && state.stack.includesRootProb) {
        finishedHeap.offer(state, state.totalProb);
      } else {
        heap.offer(state, state.totalProb);
      }
    }
  }

  private GroundedCcgParse decodeParseFromSpan(int spanStart, int spanEnd,
      int beamIndex, CcgChart chart, CcgParser parser) {
    DiscreteVariable syntaxVarType = parser.getSyntaxVarType();
    ChartEntry entry = chart.getChartEntriesForSpan(spanStart, spanEnd)[beamIndex];
    HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) syntaxVarType.getValue(
        entry.getHeadedSyntax());

    IncEvalState evalState = null; 
    if (entry.getAdditionalInfo() != null) {
      evalState = (IncEvalState) entry.getAdditionalInfo();
    }

    if (entry.isTerminal()) {
      List<String> terminals = chart.getWords();
      List<String> posTags = chart.getPosTags();
      // rightSpanStart and rightSpanEnd are used to track the trigger span
      // in chart entries for terminals.
      LexiconEntryInfo lexiconEntryInfo = new LexiconEntryInfo(entry.getLexiconEntry(),
          entry.getLexiconTrigger(), entry.getLexiconIndex(), spanStart, spanEnd,
          entry.getRightSpanStart(), entry.getRightSpanEnd());

      return GroundedCcgParse.forTerminal(syntax, lexiconEntryInfo, posTags.subList(spanStart, spanEnd + 1),
          parser.variableToIndexedPredicateArray(syntax.getHeadVariable(), entry.getAssignments()),
          Arrays.asList(parser.longArrayToFilledDependencyArray(entry.getDependencies())),
          terminals.subList(spanStart, spanEnd + 1), chart.getChartEntryProbsForSpan(spanStart, spanEnd)[beamIndex],
          entry.getRootUnaryRule(), spanStart, spanEnd, evalState);
    } else {
      GroundedCcgParse left = decodeParseFromSpan(entry.getLeftSpanStart(), entry.getLeftSpanEnd(),
          entry.getLeftChartIndex(), chart, parser);
      GroundedCcgParse right = decodeParseFromSpan(entry.getRightSpanStart(), entry.getRightSpanEnd(),
          entry.getRightChartIndex(), chart, parser);

      if (entry.getLeftUnaryRule() != null) {
        left = left.addUnaryRule(entry.getLeftUnaryRule(), (HeadedSyntacticCategory)
            syntaxVarType.getValue(entry.getLeftUnaryRule().getSyntax()));
      }
      if (entry.getRightUnaryRule() != null) {
        right = right.addUnaryRule(entry.getRightUnaryRule(), (HeadedSyntacticCategory)
            syntaxVarType.getValue(entry.getRightUnaryRule().getSyntax()));
      }

      double nodeProb = chart.getChartEntryProbsForSpan(spanStart, spanEnd)[beamIndex] /
          (left.getSubtreeProbability() * right.getSubtreeProbability());

      return GroundedCcgParse.forNonterminal(syntax,
          parser.variableToIndexedPredicateArray(syntax.getHeadVariable(), entry.getAssignments()),
          Arrays.asList(parser.longArrayToFilledDependencyArray(entry.getDependencies())), nodeProb,
          left, right, entry.getCombinator(), entry.getRootUnaryRule(), spanStart, spanEnd, evalState);
    }
  }

  private static final class StateSize implements Function<State, Integer> {
    private final int maxStackSize;
    public StateSize(int maxStackSize) {
      this.maxStackSize = maxStackSize;
    }

    @Override
    public Integer apply(State state) {
      int size = state.stack.size;
      if (size < maxStackSize) {
        return size;
      } else {
        return -1;
      }
    }
  }
}
