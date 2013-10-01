package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.jayantkrish.jklol.ccg.chart.ChartFilter;
import com.jayantkrish.jklol.ccg.chart.ConjunctionChartFilter;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartFilter;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.supertag.SupertagChartFilter;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.training.LogFunction;

public class CcgBeamSearchInference implements CcgInference {

  // Optional constraint to use during inference. Null if
  // no constraints are imposed on the search.
  private final ChartFilter searchFilter;

  // Size of the beam used during inference (which uses beam search).
  private final int beamSize;
  
  // Maximum number of milliseconds to spend parsing a single sentence.
  private final long maxParseTimeMillis;
  
  public CcgBeamSearchInference(ChartFilter searchFilter, int beamSize, long maxParseTimeMillis) {
    this.searchFilter = searchFilter;
    this.beamSize = beamSize;
    this.maxParseTimeMillis = maxParseTimeMillis;
  }

  @Override
  public CcgParse getBestParse(CcgParser parser, SupertaggedSentence sentence,
      ChartFilter chartFilter, LogFunction log) {
    ChartFilter filter = ConjunctionChartFilter.create(searchFilter, chartFilter,
        new SupertagChartFilter(sentence.getSupertags()));

    List<CcgParse> parses = parser.beamSearch(sentence.getWords(), sentence.getPosTags(),
        beamSize, filter, log, maxParseTimeMillis);
    if (parses.size() > 0) {
      return parses.get(0);
    } else {
      return null;
    }
  }

  @Override
  public CcgParse getBestConditionalParse(CcgParser parser, SupertaggedSentence sentence,
      ChartFilter chartFilter, LogFunction log, CcgSyntaxTree observedSyntacticTree,
      Set<DependencyStructure> observedDependencies, Expression observedLogicalForm) {

    List<CcgParse> possibleParses = null; 
    if (observedSyntacticTree != null) {
      ChartFilter conditionalChartFilter = ConjunctionChartFilter.create(new SyntacticChartFilter(observedSyntacticTree),
          new SupertagChartFilter(sentence.getSupertags()), searchFilter);
      possibleParses = parser.beamSearch(sentence.getWords(), sentence.getPosTags(), beamSize,
          conditionalChartFilter, log, -1);
    } else {
      ChartFilter conditionalChartFilter = ConjunctionChartFilter.create(
          new SupertagChartFilter(sentence.getSupertags()), searchFilter);
      possibleParses = parser.beamSearch(sentence.getWords(), sentence.getPosTags(), beamSize,
          conditionalChartFilter, log, -1);
    }

    possibleParses = CcgLoglikelihoodOracle.filterSemanticallyCompatibleParses(
        observedDependencies, observedLogicalForm, possibleParses);

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
