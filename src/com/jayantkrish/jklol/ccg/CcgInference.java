package com.jayantkrish.jklol.ccg;

import java.util.Set;

import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
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
   * Finds the best parse of a supertagged {@code sentence},
   * conditioned on observing any of the true syntactic tree,
   * set of dependencies, and correct logical form for the sentence.
   * 
   * @param parser
   * @param sentence
   * @param chartFilter
   * @param log
   * @param observedSyntacticTree
   * @param lexiconEntries
   * @param observedDependencies
   * @param observedLogicalForm
   * @return
   */
  public CcgParse getBestConditionalParse(CcgParser parser, AnnotatedSentence sentence,
      ChartCost chartFilter, LogFunction log, CcgSyntaxTree observedSyntacticTree,
      LexiconEntryLabels lexiconEntries, Set<DependencyStructure> observedDependencies,
      Expression2 observedLogicalForm);
}