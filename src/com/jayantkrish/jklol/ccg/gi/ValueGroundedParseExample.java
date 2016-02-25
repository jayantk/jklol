package com.jayantkrish.jklol.ccg.gi;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class ValueGroundedParseExample implements GroundedParseExample {
  
  private final AnnotatedSentence sentence;
  private final Object diagram;

  // Expected denotation of the parse
  private final Object denotationLabel;
  
  public ValueGroundedParseExample(AnnotatedSentence sentence, Object diagram, 
      Object denotationLabel) {
    this.sentence = Preconditions.checkNotNull(sentence);
    this.diagram = diagram;
    this.denotationLabel = denotationLabel;
  }

  @Override
  public AnnotatedSentence getSentence() {
    return sentence;
  }

  @Override
  public Object getDiagram() {
    return diagram;
  }

  @Override
  public GroundedParseCost getMarginCost() {
    return null;
  }

  @Override
  public GroundedParseCost getLabelCost() {
    return null;
  }

  @Override
  public ChartCost getChartFilter() {
    return null;
  }
  
  @Override
  public boolean isCorrectDenotation(Object denotation, Object diagram) {
    return denotationLabel.equals(denotation);
  }
}
