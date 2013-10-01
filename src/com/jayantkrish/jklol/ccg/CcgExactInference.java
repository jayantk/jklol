package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartFilter;
import com.jayantkrish.jklol.ccg.chart.ConjunctionChartFilter;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartFilter;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.training.LogFunction;

public class CcgExactInference implements CcgInference {

  // Optional constraint to use during inference. Null if
  // no constraints are imposed on the search.
  private final ChartFilter searchFilter;

  // Maximum number of milliseconds to spend parsing a single sentence.
  private final long maxParseTimeMillis;

  public CcgExactInference(ChartFilter searchFilter, long maxParseTimeMillis) {
    this.searchFilter = searchFilter;
    this.maxParseTimeMillis = maxParseTimeMillis;
  }

  @Override
  public CcgParse getBestParse(CcgParser parser, List<String> words,
      List<String> posTags, ChartFilter chartFilter, LogFunction log) {
    ChartFilter filter = ConjunctionChartFilter.create(searchFilter, chartFilter);

    return parser.parse(words, posTags, filter, log, maxParseTimeMillis);
  }

  @Override
  public CcgParse getBestConditionalParse(CcgParser parser, List<String> words,
      List<String> posTags, ChartFilter chartFilter, LogFunction log,
      CcgSyntaxTree observedSyntacticTree, Set<DependencyStructure> observedDependencies,
      Expression observedLogicalForm) {
    Preconditions.checkArgument(observedDependencies == null && observedLogicalForm == null);

    List<CcgParse> possibleParses = null; 
    if (observedSyntacticTree != null) {
      ChartFilter conditionalChartFilter = ConjunctionChartFilter.create(
          new SyntacticChartFilter(observedSyntacticTree), searchFilter);
      possibleParses = parser.beamSearch(words, posTags, 100, conditionalChartFilter, log, -1);
    } else {
      possibleParses = parser.beamSearch(words, posTags, 100, searchFilter, log, -1);
    }

    System.out.println("num correct: " + possibleParses.size());
    for (CcgParse correctParse : possibleParses) {
      System.out.println("correct: " + correctParse);
    }

    if (possibleParses.size() > 0) {
      return possibleParses.get(0);
    } else {
      return null;
    }
  }
}
