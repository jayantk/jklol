package com.jayantkrish.jklol.experiments.p3;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.gi.GroundedParseExample;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.Assignment;

public class P3Example implements GroundedParseExample {

  private final AnnotatedSentence sentence;
  private final Object diagram;

  // Expected denotation of the parse
  private final Object denotationLabel;
  private final KbState diagramLabel;
  private final KbStateCost diagramCost;

  public P3Example(AnnotatedSentence sentence, Object diagram, 
      Object denotationLabel, KbState diagramLabel) {
    this.sentence = Preconditions.checkNotNull(sentence);
    this.diagram = diagram;
    this.denotationLabel = denotationLabel;
    this.diagramLabel = diagramLabel;
    
    if (diagramLabel != null) {
      diagramCost = new KbStateCost(diagramLabel);
    } else {
      diagramCost = null;
    }
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
    return diagramCost;
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
  
  public KbState getDiagramLabel() {
    return diagramLabel;
  }
  
  private static class KbStateCost implements IncEvalCost {
    private final KbState label;
    
    public KbStateCost(KbState label) {
      this.label = Preconditions.checkNotNull(label);
    }
    
    public double apply(IncEvalState state) {
      KbState diagram = (KbState) state.getDiagram();
      
      for (String category : diagram.getCategories()) {
        DiscreteFactor assignment = diagram.getCategoryAssignment(category);
        DiscreteFactor labelAssignment = label.getCategoryAssignment(category);
        
        double numAssigned = assignment.innerProduct(assignment)
            .getUnnormalizedProbability(Assignment.EMPTY);
        double labelEqual = assignment.innerProduct(labelAssignment)
            .getUnnormalizedProbability(Assignment.EMPTY);
        
        // Use a small tolerance for double equality here.
        if (Math.abs(numAssigned - labelEqual) > 0.0001) {
          return Double.NEGATIVE_INFINITY;
        }
      }
      
      for (String relation : diagram.getRelations()) {
        DiscreteFactor assignment = diagram.getRelationAssignment(relation);
        DiscreteFactor labelAssignment = label.getRelationAssignment(relation);

        double numAssigned = assignment.innerProduct(assignment)
            .getUnnormalizedProbability(Assignment.EMPTY);
        double labelEqual = assignment.innerProduct(labelAssignment)
            .getUnnormalizedProbability(Assignment.EMPTY);
        
        // Use a small tolerance for double equality here.
        if (Math.abs(numAssigned - labelEqual) > 0.0001) {
          return Double.NEGATIVE_INFINITY;
        }
      }
      
      return 0.0;
    }
  }
}
