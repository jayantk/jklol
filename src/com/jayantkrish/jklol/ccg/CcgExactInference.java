package com.jayantkrish.jklol.ccg;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartFilter;
import com.jayantkrish.jklol.ccg.chart.ConjunctionChartFilter;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartFilter;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.supertag.SupertagChartFilter;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.training.LogFunction;

public class CcgExactInference implements CcgInference {

  // Optional constraint to use during inference. Null if
  // no constraints are imposed on the search.
  private final ChartFilter searchFilter;

  // Maximum number of milliseconds to spend parsing a single sentence.
  private final long maxParseTimeMillis;
  
  // Maximum number of chart entries for a single sentence.
  private final int maxChartSize;

  public CcgExactInference(ChartFilter searchFilter, long maxParseTimeMillis, int maxChartSize) {
    this.searchFilter = searchFilter;
    this.maxParseTimeMillis = maxParseTimeMillis;
    this.maxChartSize = maxChartSize;
  }

  @Override
  public CcgParse getBestParse(CcgParser parser, SupertaggedSentence sentence,
      ChartFilter chartFilter, LogFunction log) {
    ChartFilter filter = ConjunctionChartFilter.create(searchFilter, chartFilter,
        new SupertagChartFilter(sentence.getSupertags()));

    return parser.parse(sentence.getWords(), sentence.getPosTags(), filter, log,
        maxParseTimeMillis, maxChartSize);
  }

  @Override
  public CcgParse getBestConditionalParse(CcgParser parser, SupertaggedSentence sentence,
      ChartFilter chartFilter, LogFunction log, CcgSyntaxTree observedSyntacticTree,
      Set<DependencyStructure> observedDependencies, Expression observedLogicalForm) {
    Preconditions.checkArgument(observedDependencies == null && observedLogicalForm == null);

    CcgParse bestParse = null; 
    if (observedSyntacticTree != null) {
      ChartFilter conditionalChartFilter = ConjunctionChartFilter.create(
          new SyntacticChartFilter(observedSyntacticTree), searchFilter,
          new SupertagChartFilter(sentence.getSupertags()));
      bestParse = parser.parse(sentence.getWords(), sentence.getPosTags(), 
          conditionalChartFilter, log, maxParseTimeMillis, maxChartSize);
    } else {
      ChartFilter conditionalChartFilter = ConjunctionChartFilter.create(
          new SupertagChartFilter(sentence.getSupertags()), searchFilter);
      bestParse = parser.parse(sentence.getWords(), sentence.getPosTags(), 
          conditionalChartFilter, log, maxParseTimeMillis, maxChartSize);
    }
    
    // Note that bestParse may still be null, if parsing failed.
    return bestParse;
  }
}
