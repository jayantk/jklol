package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.jayantkrish.jklol.ccg.chart.ChartFilter;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * An inference algorithm for CCG parsing a sentence.
 * 
 * @author jayant
 *
 */
public interface CcgInference {

  public CcgParse getBestParse(CcgParser parser, List<String> words, List<String> posTags,
      ChartFilter chartFilter, LogFunction log);
  
  public CcgParse getBestConditionalParse(CcgParser parser, List<String> words,
      List<String> posTags, ChartFilter chartFilter, LogFunction log,
      CcgSyntaxTree observedSyntacticTree, Set<DependencyStructure> observedDependencies,
      Expression observedLogicalForm);
}
