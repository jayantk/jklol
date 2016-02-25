package com.jayantkrish.jklol.ccg.gi;

import java.util.List;

import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;

public interface GroundedParserInference {

  public List<GroundedCcgParse> beamSearch(GroundedParser parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, GroundedParseCost evalCost, LogFunction log);
  
  public List<GroundedCcgParse> beamSearch(GroundedParser parser, AnnotatedSentence sentence,
      Object initialDiagram);
  
  public List<GroundedCcgParse> beamSearch(GroundedParser parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, GroundedParseCost evalCost);

}
