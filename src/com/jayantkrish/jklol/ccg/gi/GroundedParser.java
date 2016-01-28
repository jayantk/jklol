package com.jayantkrish.jklol.ccg.gi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
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
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.KbestHeap;

public class GroundedParser {
  private final CcgParser parser;
  private final IncrementalEval incrEval;

  private final int TEMP_HEAP_MAX_SIZE=1000;
  
  public GroundedParser(CcgParser parser, IncrementalEval incrEval) {
    this.parser = Preconditions.checkNotNull(parser);
    this.incrEval = Preconditions.checkNotNull(incrEval);
  }
  
  public List<GroundedCcgParse> beamSearch(AnnotatedSentence sentence, Object initialDiagram, int beamSize) {
    return beamSearch(sentence, initialDiagram, beamSize, null, null);
  }

  public List<GroundedCcgParse> beamSearch(AnnotatedSentence sentence,
      Object initialDiagram, int beamSize, ChartCost chartFilter, LogFunction log) {
    CcgLeftToRightChart chart = new CcgLeftToRightChart(sentence, Integer.MAX_VALUE);
    parser.initializeChart(chart, sentence, chartFilter);
    parser.initializeChartTerminals(chart, sentence);

    // Working heap for queuing parses to process next.
    KbestHeap<State> heap = new KbestHeap<State>(beamSize, new State[0]);
    heap.offer(new State(ShiftReduceStack.empty(), initialDiagram, null, null, null, 1.0), 1.0);
    
    // Heap for finished parses.
    KbestHeap<State> finishedHeap = new KbestHeap<State>(beamSize, new State[0]);

    // Temporary heap for interfacing with CcgShiftReduceInference.
    KbestHeap<ShiftReduceStack> tempHeap = new KbestHeap<ShiftReduceStack>(TEMP_HEAP_MAX_SIZE,
        new ShiftReduceStack[0]);
    
    // Array of elements in the current beam.
    State[] currentBeam = new State[beamSize + 1];
    int currentBeamSize = 0;

    while (heap.size() > 0) {
      // Copy the heap to the current beam.
      State[] keys = heap.getKeys();
      for (int i = 0; i < heap.size(); i++) {
        currentBeam[i] = keys[i];
      }
      
      // System.out.println();
      // System.out.println("LOOP: " + heapSize);

      // Empty the heap.
      currentBeamSize = heap.size();
      heap.clear();
      
      for (int i = 0; i < currentBeamSize; i++) {
        State state = currentBeam[i];
        
        if (state.continuation != null) {
          incrEval.evaluateContinuation(state, heap, chart, parser);
        } else if (state.stack.includesRootProb) {
          // This state is a finished state: it spans the entire sentence
          // and evaluation has finished.
          finishedHeap.offer(state, state.totalProb);
        } else {
          // System.out.println("Processing " + stack);
          tempHeap.clear();
          
          CcgShiftReduceInference.shift(state.stack, chart, tempHeap);
          CcgShiftReduceInference.reduce(state.stack, chart, tempHeap, parser, log);
          // Applying unary rules at the root does not end parsing in this model,
          // as we may need to evaluate the logical form produced at the root.
          CcgShiftReduceInference.root(state.stack, chart, tempHeap, parser);
          
          // Ensure that we didn't discard any candidate parses due to the
          // capped temporary heap size.
          Preconditions.checkState(tempHeap.size() < TEMP_HEAP_MAX_SIZE);
          
          ShiftReduceStack[] tempHeapKeys = tempHeap.getKeys();
          for (int j = 0; j < tempHeap.size(); j++) {
            ShiftReduceStack result = tempHeapKeys[j];
            
            double prob = chart.getChartEntryProbsForSpan(result.spanStart, result.spanEnd)[result.chartEntryIndex];
            
            Object continuation = null;
            Environment continuationEnv = null;
            HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) parser.getSyntaxVarType()
                .getValue(result.entry.getHeadedSyntax());
            
            System.out.println("shift/reduce: " + syntax + " " + prob);

            if (incrEval.isEvaluatable(syntax)) {
              GroundedCcgParse parse = decodeParseFromSpan(result.spanStart, result.spanEnd,
                  result.chartEntryIndex, chart, parser);
              
              continuationEnv = incrEval.getEnvironment(state);
              continuation = incrEval.parseToContinuation(parse, continuationEnv);

              if (continuation == null) {
                continuationEnv = null;
              }
            }

            State next = new State(result, state.diagram, continuation, null, continuationEnv, state.evalProb);
            // TODO: do we need to score this?
            heap.offer(next, next.totalProb);
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
  
  private GroundedCcgParse decodeParseFromSpan(int spanStart, int spanEnd,
      int beamIndex, CcgChart chart, CcgParser parser) {
        DiscreteVariable syntaxVarType = parser.getSyntaxVarType();
    ChartEntry entry = chart.getChartEntriesForSpan(spanStart, spanEnd)[beamIndex];
    HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) syntaxVarType.getValue(
        entry.getHeadedSyntax());
    
    Object denotationValue = entry.getAdditionalInfo();
    Object denotation = null;
    if (denotationValue != null) {
      denotation = ((DenotationValue) denotationValue).getDenotation();
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
          entry.getRootUnaryRule(), spanStart, spanEnd, denotation);
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
          left, right, entry.getCombinator(), entry.getRootUnaryRule(), spanStart, spanEnd, denotation);
    }
  }

  public static class State {
    public final ShiftReduceStack stack;
    
    public final Object diagram;
    public final Object continuation;
    public final Object denotation;
    public final Environment continuationEnv;
    public final double evalProb;
    
    public final double totalProb;
    
    public State(ShiftReduceStack stack, Object diagram, Object continuation,
        Object denotation, Environment continuationEnv, double evalProb) {
      this.stack = Preconditions.checkNotNull(stack);
      this.diagram = diagram;
      this.continuation = continuation;
      this.denotation = denotation;
      this.continuationEnv = continuationEnv;
      this.evalProb = evalProb;
      
      this.totalProb = stack.totalProb * evalProb;
      
      // Both continuation and continuationEnv must be null or not-null.
      Preconditions.checkArgument(!(continuation == null ^ continuationEnv == null));
    }
  }
}
