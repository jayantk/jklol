package com.jayantkrish.jklol.ccg.gi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.KbestHeap;

public class GroundedParser {
  private final CcgParser parser;
  private final IncEval incrEval;

  private final int TEMP_HEAP_MAX_SIZE=100000;
  
  public GroundedParser(CcgParser parser, IncEval incrEval) {
    this.parser = Preconditions.checkNotNull(parser);
    this.incrEval = Preconditions.checkNotNull(incrEval);
  }
  
  public CcgParser getCcgParser() {
    return parser;
  }
  
  public IncEval getEval() {
    return incrEval;
  }
  
  public List<GroundedCcgParse> beamSearch(AnnotatedSentence sentence, Object initialDiagram, int beamSize) {
    return beamSearch(sentence, initialDiagram, beamSize, null, null, null);
  }

  public List<GroundedCcgParse> beamSearch(AnnotatedSentence sentence,
      Object initialDiagram, int beamSize, ChartCost chartFilter, 
      Predicate<State> evalFilter, LogFunction log) {
    CcgLeftToRightChart chart = new CcgLeftToRightChart(sentence, Integer.MAX_VALUE);
    parser.initializeChart(chart, sentence, chartFilter);
    parser.initializeChartTerminals(chart, sentence);

    // Working heap for queuing parses to process next.
    KbestHeap<State> heap = new KbestHeap<State>(beamSize, new State[0]);
    heap.offer(new State(ShiftReduceStack.empty(), initialDiagram, incrEval.getEnvironment(), null), 1.0);
    
    // Heap for finished parses.
    KbestHeap<State> finishedHeap = new KbestHeap<State>(beamSize, new State[0]);

    // Temporary heap for interfacing with CcgShiftReduceInference.
    KbestHeap<ShiftReduceStack> tempHeap = new KbestHeap<ShiftReduceStack>(TEMP_HEAP_MAX_SIZE,
        new ShiftReduceStack[0]);
    
    // Temporary list of evaluation results for interfacing with IncrementalEval
    List<IncEvalState> tempEvalResults = Lists.newArrayList();
    
    // Array of elements in the current beam.
    State[] currentBeam = new State[beamSize + 1];
    int currentBeamSize = 0;

    while (heap.size() > 0) {
      // Copy the heap to the current beam.
      State[] keys = heap.getKeys();
      for (int i = 0; i < heap.size(); i++) {
        currentBeam[i] = keys[i];
      }

      // Empty the heap.
      currentBeamSize = heap.size();
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
          // System.out.println("eval: " + curSyntax + " " + curSpanStart + "," + curSpanEnd);
          tempEvalResults.clear();
          incrEval.evaluateContinuation(state.evalResult, tempEvalResults);
          
          for (IncEvalState result : tempEvalResults) {
            queueEvalState(result, state.stack, heap, chart, evalFilter);
          }

        } else if (state.stack.includesRootProb) {
          // System.out.println("root: " + curSyntax + " " + curSpanStart + "," + curSpanEnd);
          // This state is a finished state: it spans the entire sentence
          // and evaluation has finished.
          offer(finishedHeap, state, evalFilter);
        } else {
          tempHeap.clear();
          
          CcgShiftReduceInference.shift(state.stack, chart, tempHeap);
          CcgShiftReduceInference.reduce(state.stack, chart, tempHeap, parser, log);
          // Applying unary rules at the root does not end parsing in this model,
          // as we may need to evaluate the logical form produced at the root.
          CcgShiftReduceInference.root(state.stack, chart, tempHeap, parser);
          
          // Ensure that we didn't discard any candidate parses due to the
          // capped temporary heap size.
          Preconditions.checkState(tempHeap.size() < TEMP_HEAP_MAX_SIZE);
          
          // System.out.println("shift/reducing: " + curSyntax + " " + curSpanStart + "," + curSpanEnd);

          ShiftReduceStack[] tempHeapKeys = tempHeap.getKeys();
          for (int j = 0; j < tempHeap.size(); j++) {
            ShiftReduceStack result = tempHeapKeys[j];
            
            Object continuation = null;
            Environment continuationEnv = null;
            HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) parser.getSyntaxVarType()
                .getValue(result.entry.getHeadedSyntax());
            
            // System.out.println("  : " + result.spanStart + "," + result.spanEnd + " " + syntax);

            if (incrEval.isEvaluatable(syntax)) {
              GroundedCcgParse parse = decodeParseFromSpan(result.spanStart, result.spanEnd,
                  result.chartEntryIndex, chart, parser);
              
              continuationEnv = Environment.extend(state.env);
              continuation = incrEval.parseToContinuation(parse, continuationEnv);
            }

            State next;
            if (continuation != null) {
              IncEvalState r = new IncEvalState(continuation, continuationEnv,
                  null, state.diagram, 1.0, null);
              next = new State(result, state.diagram, null, r);
            } else {
              next = new State(result, state.diagram, state.env, null);
            }

            offer(heap, next, evalFilter);
          }
        }
      }
    }
    
    List<GroundedCcgParse> parses = Lists.newArrayList();
    while (finishedHeap.size() > 0) {
      State state = finishedHeap.removeMin();
      ShiftReduceStack stack = state.stack;
      GroundedCcgParse parse = decodeParseFromSpan(stack.spanStart, stack.spanEnd, stack.chartEntryIndex, chart, parser);
      parses.add(parse.addDiagram(state.diagram));
    }
    Collections.reverse(parses);

    return parses;
  }
  
  /**
   * Queues a search state containing the result of an evaluation. 
   * 
   * @param evalResult
   * @param cur
   * @param heap
   * @param chart
   * @param evalFilter
   */
  private void queueEvalState(IncEvalState evalResult, ShiftReduceStack cur,
      KbestHeap<State> heap, CcgChart chart, Predicate<State> evalFilter) {
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
      chart.addChartEntryForSpan(entry, entryProb, spanStart, spanEnd, parser.getSyntaxVarType());

      ShiftReduceStack newStack = cur.previous.push(cur.spanStart, cur.spanEnd, entryIndex,
          entry, entryProb, cur.includesRootProb);

      State next = new State(newStack, evalResult.getDiagram(), evalResult.getEnvironment(), null);
      offer(heap, next, evalFilter);
    } else {
      // Evaluation is still in progress. 
      State next = new State(cur, null, null, evalResult);
      offer(heap, next, evalFilter);
    }
  }
  
  private static final void offer(KbestHeap<State> heap, State state,
      Predicate<State> filter) {
    if (filter == null || filter.apply(state)) {
      heap.offer(state, state.totalProb);
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

  /**
   * A search state for joint CCG parsing and incremental evaluation.
   * 
   * @author jayantk
   */
  public static class State {
    /**
     * Current state of the shift reduce CCG parser.
     */
    public final ShiftReduceStack stack;
    public final Object diagram;
    public final Environment env;
    
    /**
     * Current state of incremental evaluation. If {@code null},
     * the next search actions are parser actions; otherwise,
     * the next actions are evaluation actions. 
     */
    public final IncEvalState evalResult;
    
    public final double totalProb;
    
    public State(ShiftReduceStack stack, Object diagram, Environment env, IncEvalState evalResult) {
      this.stack = Preconditions.checkNotNull(stack);
      this.diagram = diagram;
      this.env = env;
      this.evalResult = evalResult;
      
      if (evalResult != null) {
        this.totalProb = stack.totalProb * evalResult.getProb();
      } else {
        this.totalProb = stack.totalProb;
      }
    }
  }
}
