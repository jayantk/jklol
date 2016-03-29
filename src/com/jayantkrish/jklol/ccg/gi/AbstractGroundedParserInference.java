package com.jayantkrish.jklol.ccg.gi;

import java.util.List;

import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.NullLogFunction;

public abstract class AbstractGroundedParserInference implements GroundedParserInference {

  @Override
  public List<GroundedCcgParse> beamSearch(GroundedParser parser, AnnotatedSentence sentence,
      Object initialDiagram) {
    return beamSearch(parser, sentence, initialDiagram, null, null, new NullLogFunction());
  }
  
  @Override
  public List<GroundedCcgParse> beamSearch(GroundedParser parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, IncEvalCost evalCost) {
    return beamSearch(parser, sentence, initialDiagram, chartFilter, evalCost, new NullLogFunction());
  }
}
