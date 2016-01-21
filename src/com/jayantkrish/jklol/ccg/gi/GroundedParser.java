package com.jayantkrish.jklol.ccg.gi;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgShiftReduceInference;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.ShiftReduceStack;
import com.jayantkrish.jklol.ccg.chart.CcgLeftToRightChart;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
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
  
  public List<CcgParse> beamSearch(AnnotatedSentence sentence, Object initialDiagram, int beamSize) {
    return beamSearch(sentence, initialDiagram, beamSize, null, null);
  }

  public List<CcgParse> beamSearch(AnnotatedSentence sentence,
      Object initialDiagram, int beamSize, ChartCost chartFilter, LogFunction log) {
    CcgLeftToRightChart chart = new CcgLeftToRightChart(sentence, Integer.MAX_VALUE);
    parser.initializeChart(chart, sentence, chartFilter);
    parser.initializeChartTerminals(chart, sentence);

    // Working heap for queuing parses to process next.
    KbestHeap<State> heap = new KbestHeap<State>(beamSize, new State[0]);
    heap.offer(new State(ShiftReduceStack.empty(), initialDiagram, null, null, 1.0), 1.0);
    
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
          incrEval.evaluateContinuation(state, heap);
        } else {
          // System.out.println("Processing " + stack);
          tempHeap.clear();
          
          CcgShiftReduceInference.shift(state.stack, chart, tempHeap);
          CcgShiftReduceInference.reduce(state.stack, chart, tempHeap, parser, log);
          
          // Ensure that we didn't discard any candidate parses due to the
          // capped temporary heap size.
          Preconditions.checkState(tempHeap.size() < TEMP_HEAP_MAX_SIZE);
          
          ShiftReduceStack[] tempHeapKeys = tempHeap.getKeys();
          for (int j = 0; j < tempHeap.size(); j++) {
            ShiftReduceStack result = tempHeapKeys[j];
            
            Object continuation = null;
            Environment continuationEnv = null;
            HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) parser.getSyntaxVarType()
                .getValue(result.entry.getHeadedSyntax());
            if (incrEval.isEvaluatable(syntax)) {
              CcgParse parse = chart.decodeParseFromSpan(result.spanStart, result.spanEnd,
                  result.chartEntryIndex, parser);
              Expression2 lf = parse.getLogicalForm();
              
              if (lf != null) {
                continuation = incrEval.lfToContinuation(lf);
                continuationEnv = incrEval.getEnvironment(state);
              }
            }

            State next = new State(result, state.diagram, continuation, continuationEnv, state.evalProb);
            // TODO: do we need to score this?
            heap.offer(next, next.totalProb);
          }
        }
      }
    }

    parser.reweightRootEntries(chart);
    // System.out.println("NUM ROOT: " + chart.getNumChartEntriesForSpan(0, chart.size() - 1));
    
    return chart.decodeBestParsesForSpan(0, chart.size() - 1, beamSize, parser);
  }
  
  public static class State {
    public final ShiftReduceStack stack;
    
    public final Object diagram;
    public final Object continuation;
    public final Environment continuationEnv;
    public final double evalProb;
    
    public final double totalProb;
    
    public State(ShiftReduceStack stack, Object diagram, Object continuation,
        Environment continuationEnv, double evalProb) {
      this.stack = Preconditions.checkNotNull(stack);
      this.diagram = diagram;
      this.continuation = continuation;
      this.continuationEnv = continuationEnv;
      this.evalProb = evalProb;
      
      this.totalProb = stack.totalProb * evalProb;
      
      // Both continuation and continuationEnv must be null or not-null.
      Preconditions.checkArgument(!(continuation == null ^ continuationEnv == null));
    }
  }
}
