package com.jayantkrish.jklol.ccg.gi;

import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public interface GroundedParseExample {

  public AnnotatedSentence getSentence();

  public Object getDiagram();  

  public Predicate<State> getEvalFilter();

  public ChartCost getChartFilter();
  
  /**
   * Returns {@code true} for correct denotations  
   * 
   * @param denotation
   * @param diagram
   * @return
   */
  public boolean isCorrectDenotation(Object denotation, Object diagram);
}
