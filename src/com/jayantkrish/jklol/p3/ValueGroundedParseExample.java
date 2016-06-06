package com.jayantkrish.jklol.p3;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class ValueGroundedParseExample implements P3Example {
  
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
  public IncEvalCost getMarginCost() {
    return null;
  }

  @Override
  public IncEvalCost getLabelCost() {
    return null;
  }

  @Override
  public ChartCost getChartFilter() {
    return null;
  }
  
  @Override
  public boolean isCorrect(Expression2 lf, Object denotation, Object diagram) {
    return denotationLabel.equals(denotation);
  }
  
  public Object getLabel() {
    return denotationLabel;
  }
}
