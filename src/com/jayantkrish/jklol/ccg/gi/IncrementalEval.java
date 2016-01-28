package com.jayantkrish.jklol.ccg.gi;

import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.ShiftReduceStack;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.util.KbestHeap;

public interface IncrementalEval {

  public void evaluateContinuation(State state, KbestHeap<State> heap, CcgChart chart,
      CcgParser parser);
  
  public Environment getEnvironment(State currentState);
  
  public Object parseToContinuation(GroundedCcgParse parse, Environment env);
  
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
