package com.jayantkrish.jklol.ccg.gi;

import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.ShiftReduceStack;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.util.KbestHeap;

/**
 * Oracle for evaluating logical forms during CCG parsing. 
 * 
 * @author jayantk
 *
 */
public interface IncrementalEval {

  /**
   * Evaluates the continuation stored in {@code state} queuing
   * new search states on {@code heap} for the resulting value(s). 
   * 
   * @param state
   * @param heap
   * @param chart
   * @param parser
   */
  public void evaluateContinuation(State state, KbestHeap<State> heap, CcgChart chart,
      CcgParser parser);
  
  /**
   * Gets the environment in which logical forms are evaluated.
   * The returned environment may be mutated by the calling code.
   * 
   * @return
   */
  public Environment getEnvironment();
  
  /**
   * Produces a continuation from a parse. The continuation represents
   * an evaluatable object (in the simplest case, the evaluatable expression
   * itself) that can be used in {@code evaluateContinuation}.
   * 
   * @param parse
   * @param env
   * @return
   */
  public Object parseToContinuation(GroundedCcgParse parse, Environment env);
  
  /**
   * Returns {@code true} if a CCG parse with root {@code syntax}
   * produces a logical form that should be evaluated.
   * 
   * @param syntax
   * @return
   */
  public boolean isEvaluatable(HeadedSyntacticCategory syntax);
  
  public static void queueState(Object denotation, Object diagram, double prob, ShiftReduceStack cur,
      KbestHeap<State> heap, CcgChart chart, CcgParser parser) {
    int spanStart = cur.spanStart;
    int spanEnd = cur.spanEnd;

    // Create a new chart entry storing the new denotation.
    ChartEntry entry = cur.entry.addAdditionalInfo(
        new DenotationValue(denotation, prob));
    double entryProb = cur.entryProb * prob;

    int entryIndex = chart.getNumChartEntriesForSpan(spanStart, spanEnd);
    chart.addChartEntryForSpan(entry, entryProb, spanStart, spanEnd, parser.getSyntaxVarType());

    ShiftReduceStack newStack = cur.previous.push(cur.spanStart, cur.spanEnd, entryIndex,
        entry, entryProb, cur.includesRootProb);

    State next = new State(newStack, diagram, null, denotation, null, 1.0);
    heap.offer(next, next.totalProb);
  }
}
