package com.jayantkrish.jklol.ccg;

import java.util.Set;

import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
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
  public CcgParse getBestParse(CcgParser parser, SupertaggedSentence sentence,
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
   * @param observedDependencies
   * @param observedLogicalForm
   * @return
   */
  public CcgParse getBestConditionalParse(CcgParser parser, 
      SupertaggedSentence sentence, ChartCost chartFilter, LogFunction log,
      CcgSyntaxTree observedSyntacticTree, Set<DependencyStructure> observedDependencies,
      Expression observedLogicalForm);
}