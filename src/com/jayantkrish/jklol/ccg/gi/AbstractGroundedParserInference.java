package com.jayantkrish.jklol.ccg.gi;

import java.util.List;

import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
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
      Object initialDiagram, ChartCost chartFilter, Predicate<State> evalFilter) {
    return beamSearch(parser, sentence, initialDiagram, chartFilter, evalFilter, new NullLogFunction());
  }
}
