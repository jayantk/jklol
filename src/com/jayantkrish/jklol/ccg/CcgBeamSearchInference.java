package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.SumChartCost;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartCost;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.supertag.SupertagChartCost;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.training.LogFunction;

public class CcgBeamSearchInference implements CcgInference {

  // Optional constraint to use during inference. Null if
  // no constraints are imposed on the search.
  private final ChartCost searchFilter;

  // Size of the beam used during inference (which uses beam search).
  private final int beamSize;
  
  // Maximum number of milliseconds to spend parsing a single sentence.
  private final long maxParseTimeMillis;
  
  // Maximum number of chart entries for a single sentence.
  private final int maxChartSize;
  
  // Number of threads to use while parsing.
  private final int numThreads;

  // Whether to print out information about correct parses, etc.
  private final boolean verbose;

  public CcgBeamSearchInference(ChartCost searchFilter, int beamSize, long maxParseTimeMillis,
      int maxChartSize, int numThreads, boolean verbose) {
    this.searchFilter = searchFilter;
    this.beamSize = beamSize;
    this.maxParseTimeMillis = maxParseTimeMillis;
    this.maxChartSize = maxChartSize;
    this.numThreads = numThreads;

    this.verbose = verbose;
  }

  @Override
  public CcgParse getBestParse(CcgParser parser, SupertaggedSentence sentence,
      ChartCost chartFilter, LogFunction log) {
    ChartCost filter = SumChartCost.create(searchFilter, chartFilter,
        new SupertagChartCost(sentence.getSupertags()));

    List<CcgParse> parses = parser.beamSearch(sentence, beamSize, filter, log,
        maxParseTimeMillis, maxChartSize, numThreads);
    if (parses.size() > 0) {
      return parses.get(0);
    } else {
      return null;
    }
  }

  @Override
  public CcgParse getBestConditionalParse(CcgParser parser, SupertaggedSentence sentence,
      ChartCost chartFilter, LogFunction log, CcgSyntaxTree observedSyntacticTree,
      Set<DependencyStructure> observedDependencies, Expression observedLogicalForm) {

    List<CcgParse> possibleParses = null; 
    if (observedSyntacticTree != null) {
      ChartCost conditionalChartFilter = SumChartCost.create(SyntacticChartCost.createAgreementCost(observedSyntacticTree),
          new SupertagChartCost(sentence.getSupertags()), searchFilter);
      possibleParses = parser.beamSearch(sentence, beamSize, conditionalChartFilter,
          log, -1, maxChartSize, numThreads);
    } else {
      ChartCost conditionalChartFilter = SumChartCost.create(
          new SupertagChartCost(sentence.getSupertags()), searchFilter);
      possibleParses = parser.beamSearch(sentence, beamSize, conditionalChartFilter,
          log, -1, maxChartSize, numThreads);
    }

    possibleParses = CcgLoglikelihoodOracle.filterSemanticallyCompatibleParses(
        observedDependencies, observedLogicalForm, possibleParses);

    if (verbose) {
      System.out.println("num correct: " + possibleParses.size());
      for (CcgParse correctParse : possibleParses) {
        System.out.println("correct: " + correctParse);
      }
    }

    if (possibleParses.size() > 0) {
      return possibleParses.get(0);
    } else {
      return null;
    }
  }
}
