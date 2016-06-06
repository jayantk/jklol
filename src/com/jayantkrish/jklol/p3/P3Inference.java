package com.jayantkrish.jklol.p3;

import java.util.List;

import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Interface for inference algorithms for P3.
 * 
 * @author jayantk
 *
 */
public interface P3Inference {

  public List<P3Parse> beamSearch(P3Model parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, IncEvalCost evalCost, LogFunction log);
  
  public List<P3Parse> beamSearch(P3Model parser, AnnotatedSentence sentence,
      Object initialDiagram);
  
  public List<P3Parse> beamSearch(P3Model parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, IncEvalCost evalCost);

}
