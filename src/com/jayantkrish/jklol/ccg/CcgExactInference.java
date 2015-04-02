package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.SumChartCost;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartCost;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lexinduct.LexiconChartCost;
import com.jayantkrish.jklol.ccg.supertag.SupertagChartCost;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.training.LogFunction;

public class CcgExactInference implements CcgInference {

  // Optional constraint to use during inference. Null if
  // no constraints are imposed on the search.
  private final ChartCost searchFilter;

  // Maximum number of milliseconds to spend parsing a single sentence.
  private final long maxParseTimeMillis;
  
  // Maximum number of chart entries for a single sentence.
  private final int maxChartSize;

  // Maximum number of threads to use while parsing.
  private final int numThreads;

  public CcgExactInference(ChartCost searchFilter, long maxParseTimeMillis, int maxChartSize,
      int numThreads) {
    this.searchFilter = searchFilter;
    this.maxParseTimeMillis = maxParseTimeMillis;
    this.maxChartSize = maxChartSize;
    this.numThreads = numThreads;
  }

  public static CcgExactInference createDefault() {
    return new CcgExactInference(null, -1L, Integer.MAX_VALUE, 1);
  }

  @Override
  public CcgParse getBestParse(CcgParser parser, SupertaggedSentence sentence,
      ChartCost chartFilter, LogFunction log) {
    ChartCost filter = SumChartCost.create(searchFilter, chartFilter,
        new SupertagChartCost(sentence.getSupertags()));

    return parser.parse(sentence, filter, log, maxParseTimeMillis, maxChartSize, numThreads);
  }

  @Override
  public CcgParse getBestConditionalParse(CcgParser parser, SupertaggedSentence sentence,
      ChartCost chartFilter, LogFunction log, CcgSyntaxTree observedSyntacticTree,
      List<Expression2> lexiconEntries, Set<DependencyStructure> observedDependencies,
      Expression2 observedLogicalForm) {
    Preconditions.checkArgument(observedDependencies == null && observedLogicalForm == null);

    ChartCost supertagCost = new SupertagChartCost(sentence.getSupertags());
    ChartCost syntacticCost = null;
    ChartCost lexiconCost = null;
    if (observedSyntacticTree != null) {
      syntacticCost = SyntacticChartCost.createAgreementCost(observedSyntacticTree);
    } 
    if (lexiconEntries != null) {
      lexiconCost = new LexiconChartCost(lexiconEntries);
    }
    ChartCost conditionalChartFilter = SumChartCost.create(searchFilter, chartFilter,
        supertagCost, syntacticCost, lexiconCost);

    CcgParse bestParse = parser.parse(sentence, conditionalChartFilter, log, maxParseTimeMillis,
          maxChartSize, numThreads);

    // Note that bestParse may still be null, if parsing failed.
    return bestParse;
  }
}
