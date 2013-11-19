package com.jayantkrish.jklol.ccg;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.SumChartCost;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartCost;
import com.jayantkrish.jklol.ccg.lambda.Expression;
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

  public CcgExactInference(ChartCost searchFilter, long maxParseTimeMillis, int maxChartSize) {
    this.searchFilter = searchFilter;
    this.maxParseTimeMillis = maxParseTimeMillis;
    this.maxChartSize = maxChartSize;
  }

  @Override
  public CcgParse getBestParse(CcgParser parser, SupertaggedSentence sentence,
      ChartCost chartFilter, LogFunction log) {
    ChartCost filter = SumChartCost.create(searchFilter, chartFilter,
        new SupertagChartCost(sentence.getSupertags()));

    return parser.parse(sentence.getWords(), sentence.getPosTags(), filter, log,
        maxParseTimeMillis, maxChartSize);
  }

  @Override
  public CcgParse getBestConditionalParse(CcgParser parser, SupertaggedSentence sentence,
      ChartCost chartFilter, LogFunction log, CcgSyntaxTree observedSyntacticTree,
      Set<DependencyStructure> observedDependencies, Expression observedLogicalForm) {
    Preconditions.checkArgument(observedDependencies == null && observedLogicalForm == null);

    CcgParse bestParse = null; 
    if (observedSyntacticTree != null) {
      ChartCost conditionalChartFilter = SumChartCost.create(
          SyntacticChartCost.createAgreementCost(observedSyntacticTree), searchFilter,
          new SupertagChartCost(sentence.getSupertags()));
      bestParse = parser.parse(sentence.getWords(), sentence.getPosTags(), 
          conditionalChartFilter, log, maxParseTimeMillis, maxChartSize);
    } else {
      ChartCost conditionalChartFilter = SumChartCost.create(
          new SupertagChartCost(sentence.getSupertags()), searchFilter);
      bestParse = parser.parse(sentence.getWords(), sentence.getPosTags(), 
          conditionalChartFilter, log, maxParseTimeMillis, maxChartSize);
    }
    
    // Note that bestParse may still be null, if parsing failed.
    return bestParse;
  }
}
