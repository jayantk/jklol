package com.jayantkrish.jklol.cfg;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

public class BeamSearchParseChart {

  private final List<?> terminals;
  private final int beamSize;
  private final PriorityQueue<ParseTree>[][] chart;

  @SuppressWarnings({"unchecked"})
  public BeamSearchParseChart(List<?> terminals, int beamSize) {
    this.terminals = terminals;
    this.beamSize = beamSize;
    this.chart = (PriorityQueue<ParseTree>[][]) new PriorityQueue[terminals.size()][terminals.size()];
  }

  public int chartSize() {
    return terminals.size();
  }

  public int getBeamSize() {
    return beamSize;
  }

  /**
   * Gets the parse trees spanning the terminals from {@code spanStart} to
   * {@code spanEnd}, inclusive.
   * 
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public Collection<ParseTree> getParseTreesForSpan(int spanStart, int spanEnd) {
    if (chart[spanStart][spanEnd] == null) {
      return Collections.emptyList();
    } else {
      return chart[spanStart][spanEnd];
    }
  }
  
  public void addParseTreeForSpan(int spanStart, int spanEnd, ParseTree tree) {
    if (chart[spanStart][spanEnd] == null) {
      chart[spanStart][spanEnd] = new PriorityQueue<ParseTree>(beamSize + 1);
    }
    
    if (tree.getProbability() == 0.0) {
      // Trees with zero probability can be safely ignored.
      return;
    }
    
    PriorityQueue<ParseTree> spanQueue = chart[spanStart][spanEnd]; 
    spanQueue.offer(tree);
    
    if (spanQueue.size() > beamSize) {
      // Removes the smallest entry, which is the parse tree with the least probability.
      spanQueue.poll();
    }
  }
}
