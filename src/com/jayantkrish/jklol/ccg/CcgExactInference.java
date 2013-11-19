package com.jayantkrish.jklol.ccg;

import java.util.List;
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

    List<CcgParse> possibleParses = null; 
    if (observedSyntacticTree != null) {
      ChartFilter conditionalChartFilter = ConjunctionChartFilter.create(
          new SyntacticChartFilter(observedSyntacticTree), searchFilter,
          new SupertagChartFilter(sentence.getSupertags()));
      possibleParses = parser.beamSearch(sentence.getWords(), sentence.getPosTags(), 100,
          conditionalChartFilter, log, -1, maxChartSize);
    } else {
      ChartFilter conditionalChartFilter = ConjunctionChartFilter.create(
          new SupertagChartFilter(sentence.getSupertags()), searchFilter);
      possibleParses = parser.beamSearch(sentence.getWords(), sentence.getPosTags(), 100,
          conditionalChartFilter, log, -1, maxChartSize);
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
