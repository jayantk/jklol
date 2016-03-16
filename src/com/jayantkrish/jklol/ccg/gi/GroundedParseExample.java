package com.jayantkrish.jklol.ccg.gi;

import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public interface GroundedParseExample {

  public AnnotatedSentence getSentence();

  public Object getDiagram();

  /**
   * Get a procedure for assigning cost for states
   * compared to the label of this example. The probability
   * of a state gets multiplied with exp({@code cost}).
   */
  public IncEvalCost getMarginCost();

  /**
   * This will typically return 0.0 for states matching the
   * label and {@code Double.NEGATIVE_INFINITY} otherwise.
   */
  public IncEvalCost getLabelCost();

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
