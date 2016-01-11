package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * An inference algorithm for CCG parsing a sentence.
 * 
 * @author jayant
 *
 */
public interface CcgInference {

  /**
   * Finds the best parse of a supertagged {@code sentence}. 
   * {@code chartFilter} is an optional set of additional constraints to
   * include during parsing.
   *
   * @param parser
   * @param sentence
   * @param chartFilter
   * @param log
   * @return
   */
  public CcgParse getBestParse(CcgParser parser, AnnotatedSentence sentence,
      ChartCost chartFilter, LogFunction log);
  
  /**
   * Finds a list of the (approximate) best parses for {@code sentence}.
   *   
   * @param parser
   * @param sentence
   * @param chartFilter
   * @param log
   * @return
   */
  public List<CcgParse> beamSearch(CcgParser parser, AnnotatedSentence sentence,
      ChartCost chartFilter, LogFunction log);
}